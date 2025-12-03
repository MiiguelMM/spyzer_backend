# Dockerfile - Multi-stage build for Railway

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build

# Instalar Maven
RUN apk add --no-cache maven

# Establecer directorio de trabajo
WORKDIR /app

# Copiar todo el proyecto
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
COPY src ./src

# Compilar y empaquetar (sin ejecutar tests)
# Maven descargará las dependencias automáticamente durante el build
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

# Copiar el JAR desde el stage de build
COPY --from=build /app/target/spyzer-0.0.1-SNAPSHOT.jar app.jar

# Exponer el puerto (Railway usa variable PORT dinámica)
EXPOSE 8080

# Comando para ejecutar la aplicación con perfil railway
ENTRYPOINT ["java","-Dspring.profiles.active=railway","-jar","/app.jar"]
