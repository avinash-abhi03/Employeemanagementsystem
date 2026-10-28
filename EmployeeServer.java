import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One employee. Salary rule: Net = Basic + HRA (20%) - PF (12%). */
class Employee {
    private static final double HRA_RATE = 0.20;
    private static final double PF_RATE = 0.12;

    final int id;
    final String name, email, phone, department, designation;
    final LocalDate joinDate;
    final double basic;

    Employee(int id, String name, String email, String phone,
             String department, String designation, LocalDate joinDate, double basic) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.department = department;
        this.designation = designation;
        this.joinDate = joinDate;
        this.basic = basic;
    }

    double hra() { return basic * HRA_RATE; }
    double pf()  { return basic * PF_RATE; }
    double net() { return basic + hra() - pf(); }

    /** Quote and escape a string for JSON. */
    static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
            else sb.append(c);
        }
        return sb.append('"').toString();
    }

    String toJson() {
        return String.format(Locale.US,
                "{\"id\":%d,\"name\":%s,\"email\":%s,\"phone\":%s,\"department\":%s,"
                + "\"designation\":%s,\"joinDate\":\"%s\",\"basic\":%.2f,\"hra\":%.2f,"
                + "\"pf\":%.2f,\"net\":%.2f}",
                id, q(name), q(email), q(phone), q(department), q(designation),
                joinDate, basic, hra(), pf(), net());
    }

    String toCsv() {
        return id + "," + name + "," + email + "," + phone + "," + department + ","
                + designation + "," + joinDate + "," + basic;
    }
}

public class EmployeeServer {
    private static final Map<Integer, Employee> employees = new LinkedHashMap<>();
    private static int nextId = 101;
    private static final Path DATA_FILE = Paths.get("employees.csv");
    private static final Path PAGE_FILE = Paths.get("index.html");

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", (args.length > 0 ? args[0] : "8080")));
        loadData();

        HttpServer server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/employees", EmployeeServer::handleApi);
        server.createContext("/", EmployeeServer::handlePage);
        server.start();

        System.out.println("Employee Management System is running.");
        System.out.println("Open http://localhost:" + port + " in your browser.");
        System.out.println("Press Ctrl+C to stop.");
    }

    // ---------- Web page ----------

    private static void handlePage(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            if (!path.equals("/") && !path.equals("/index.html")) {
                send(ex, 404, "text/plain", "Not found");
            } else if (!Files.exists(PAGE_FILE)) {
                send(ex, 500, "text/plain", "index.html must be in the same folder as EmployeeServer.");
            } else {
                send(ex, 200, "text/html", new String(Files.readAllBytes(PAGE_FILE), StandardCharsets.UTF_8));
            }
        } finally {
            ex.close();
        }
    }

    // ---------- REST API ----------

    private static void handleApi(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String rest = ex.getRequestURI().getPath().substring("/api/employees".length());
            if (rest.startsWith("/")) rest = rest.substring(1);

            Integer id = null;
            if (!rest.isEmpty()) {
                try {
                    id = Integer.parseInt(rest);
                } catch (NumberFormatException e) {
                    json(ex, 400, error("Invalid employee ID."));
                    return;
                }
            }

            synchronized (employees) {
                if (method.equals("GET") && id == null) {
                    List<String> items = new ArrayList<>();
                    for (Employee e : employees.values()) items.add(e.toJson());
                    json(ex, 200, "[" + String.join(",", items) + "]");

                } else if (method.equals("POST") && id == null) {
                    Employee e = build(nextId, parseForm(readBody(ex)));
                    employees.put(e.id, e);
                    nextId++;
                    saveData();
                    json(ex, 201, e.toJson());

                } else if (method.equals("PUT") && id != null) {
                    if (!employees.containsKey(id)) {
                        json(ex, 404, error("Employee not found."));
                        return;
                    }
                    Employee e = build(id, parseForm(readBody(ex)));
                    employees.put(id, e);
                    saveData();
                    json(ex, 200, e.toJson());

                } else if (method.equals("DELETE") && id != null) {
                    if (employees.remove(id) == null) {
                        json(ex, 404, error("Employee not found."));
                        return;
                    }
                    saveData();
                    json(ex, 200, "{\"deleted\":" + id + "}");

                } else {
                    json(ex, 405, error("Method not allowed."));
                }
            }
        } catch (IllegalArgumentException e) {
            json(ex, 400, error(e.getMessage()));
        } catch (Exception e) {
            json(ex, 500, error("Server error."));
        } finally {
            ex.close();
        }
    }

    /** Validate the submitted form and build an Employee, or throw with a friendly message. */
    private static Employee build(int id, Map<String, String> f) {
        String name = clean(f.get("name"));
        String dept = clean(f.get("department"));
        String desig = clean(f.get("designation"));
        String email = clean(f.get("email"));
        String phone = clean(f.get("phone"));

        if (name.isEmpty()) throw new IllegalArgumentException("Name cannot be empty.");
        if (dept.isEmpty() || desig.isEmpty())
            throw new IllegalArgumentException("Department and designation are required.");
        if (!email.isEmpty() && !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))
            throw new IllegalArgumentException("Enter a valid email address.");
        if (!phone.isEmpty() && !phone.matches("[0-9+\\- ]{7,15}"))
            throw new IllegalArgumentException("Enter a valid phone number.");

        double basic;
        try {
            basic = Double.parseDouble(clean(f.get("basic")));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Basic salary must be a number.");
        }
        if (!(basic > 0) || Double.isInfinite(basic))
            throw new IllegalArgumentException("Basic salary must be greater than zero.");

        String d = clean(f.get("joinDate"));
        LocalDate date;
        try {
            date = d.isEmpty() ? LocalDate.now() : LocalDate.parse(d);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Join date must be in YYYY-MM-DD format.");
        }
        return new Employee(id, name, email, phone, dept, desig, date, basic);
    }

    // ---------- Helpers ----------

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("[,\\r\\n]", " ").trim();
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            String k = i < 0 ? pair : pair.substring(0, i);
            String v = i < 0 ? "" : pair.substring(i + 1);
            map.put(URLDecoder.decode(k, StandardCharsets.UTF_8),
                    URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return map;
    }

    private static String error(String msg) {
        return "{\"error\":" + Employee.q(msg) + "}";
    }

    private static void json(HttpExchange ex, int code, String body) throws IOException {
        send(ex, code, "application/json", body);
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ---------- Storage (employees.csv) ----------

    private static void saveData() {
        List<String> lines = new ArrayList<>();
        for (Employee e : employees.values()) lines.add(e.toCsv());
        try {
            Files.write(DATA_FILE, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Warning: could not save data: " + e.getMessage());
        }
    }

    private static void loadData() {
        if (!Files.exists(DATA_FILE)) {
            // First run: add a few sample employees so the page is not empty.
            addSample("Ravi Kumar", "ravi@example.com", "9876543210", "IT", "Software Engineer", 50000);
            addSample("Sita Devi", "sita@example.com", "9123456780", "HR", "HR Manager", 45000);
            addSample("Anil Reddy", "anil@example.com", "9988776655", "Finance", "Accountant", 40000);
            saveData();
            return;
        }
        try {
            for (String line : Files.readAllLines(DATA_FILE, StandardCharsets.UTF_8)) {
                String[] p = line.split(",", -1);
                if (p.length != 8) continue;
                int id = Integer.parseInt(p[0]);
                employees.put(id, new Employee(id, p[1], p[2], p[3], p[4], p[5],
                        LocalDate.parse(p[6]), Double.parseDouble(p[7])));
                nextId = Math.max(nextId, id + 1);
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("Warning: could not read saved data: " + e.getMessage());
        }
    }

    private static void addSample(String name, String email, String phone,
                                  String dept, String desig, double basic) {
        employees.put(nextId, new Employee(nextId, name, email, phone, dept, desig, LocalDate.now(), basic));
        nextId++;
    }
}
