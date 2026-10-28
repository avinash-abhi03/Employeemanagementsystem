# Employee Management System

A Java HTTP server with an HTML/CSS/JavaScript interface. The server uses Java's built-in
`HttpServer` and stores employee records in `employees.csv`.

## Run locally (Windows PowerShell)

Install a JDK (Java 21 recommended), open PowerShell in this folder, and run:

```powershell
javac EmployeeServer.java
java EmployeeServer
```

Then open http://localhost:8080/

Stop the server with `Ctrl+C`.

## Deploy to Render using Docker

1. Create a GitHub repository at https://github.com/new (for example, `EmployeeManagementSystem`).
2. Upload the files from this folder to the repository's root:
   `EmployeeServer.java`, `index.html`, `employees.csv`, `Dockerfile`,
   `.dockerignore`, `.gitignore`, and `README.md`.
3. Sign in to https://render.com/ and connect your GitHub account.
4. Choose **New +** → **Web Service**, then select the repository.
5. Select **Docker** as the runtime/environment if asked. Keep the Dockerfile path as
   `./Dockerfile`, then create/deploy the service.
6. Wait for the build to finish and open the `https://...onrender.com` URL Render gives you.

The server reads Render's `PORT` environment variable and listens on `0.0.0.0`, as cloud
hosting requires.

## Important: employee data persistence

`employees.csv` is included to seed the app with your current records. The app writes changes
back to that CSV file. On container hosting, local files may be reset when the service is
redeployed, rebuilt, or its instance is replaced. Do not rely on the container filesystem as
permanent storage for real employee data. Before using this for important data, configure
persistent storage and point the app's data file to it, or migrate employee records to a
database. Treat this as a student/demo project until persistence and security are configured.

## Security note

This project is a learning/demo app. Before storing real employee information online, add
authentication/authorization, HTTPS (provided by Render's public URL), input hardening, and
reliable database backups. Do not put sensitive employee records in a public GitHub repository.
