# Build stage: compile the Spring Boot jar with Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q -DskipTests clean package

# Run stage: slim JRE only (Render free tier = 512MB RAM)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
