FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY EmployeeServer.java index.html employees.csv ./
RUN javac EmployeeServer.java
ENV PORT=8080
EXPOSE 8080
CMD ["java", "EmployeeServer"]
