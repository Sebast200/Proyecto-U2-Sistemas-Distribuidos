# Dockerfile para Nivel 1 - Surtidor
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copiar archivos Java del nivel 1
COPY *.java /app/

# Compilar
RUN javac Surtidor.java

# Crear directorios para persistencia de datos
RUN mkdir -p /app/data

# Volumen para mantener el estado
VOLUME ["/app/data"]

# Ejecutar el surtidor
CMD ["java", "Surtidor"]
