# Dockerfile

# Usamos una imagen base ligera de Java 21
FROM eclipse-temurin:21-jdk-alpine

# Definir el argumento para la ruta del JAR
ARG JAR_FILE=target/spyzer-0.0.1-SNAPSHOT.jar

# Copiar el JAR generado al contenedor
COPY ${JAR_FILE} app.jar

# Exponer el puerto (Render usa variable PORT dinámica)
EXPOSE 8080

# Comando para ejecutar la aplicación
# Render proporciona la variable PORT, Spring Boot la leerá automáticamente
ENTRYPOINT ["java","-jar","/app.jar"]
