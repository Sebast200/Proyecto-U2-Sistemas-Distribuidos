# Dockerfile para Nivel 3 - Empresa
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copiar archivos Java del nivel 3
COPY *.java /app/

# Compilar la Empresa
RUN javac Empresa.java

# Crear directorio para datos persistentes
RUN mkdir -p /app/data

# Volumen para persistencia
VOLUME ["/app/data"]

# Exponer puerto 7000 para el servidor del nivel 3
EXPOSE 7000

# Ejecutar la Empresa
CMD ["java", "Empresa"]
