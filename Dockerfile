# === ETAPA 1: Compilar el proyecto con Maven ===
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# === ETAPA 2: Imagen final solo con el JAR ===
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/parking-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djdk.tls.client.protocols=TLSv1.2", "-jar", "app.jar"]
