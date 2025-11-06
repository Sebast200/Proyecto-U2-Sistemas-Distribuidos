# Dockerfile para Nivel 2 - Distribuidor
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Instalar wget para descargar el driver JDBC de SQLite
RUN apk add --no-cache wget

# Descargar el driver JDBC de SQLite
RUN wget https://github.com/xerial/sqlite-jdbc/releases/download/3.43.0.0/sqlite-jdbc-3.43.0.0.jar -O /app/sqlite-jdbc.jar

# Copiar archivos Java del nivel 2
COPY *.java /app/

# Compilar el Distribuidor con el driver de SQLite en el classpath
RUN javac -cp ".:/app/sqlite-jdbc.jar" Distribuidor.java

# Crear directorio para datos persistentes
RUN mkdir -p /app/data

# Volumen para persistencia
VOLUME ["/app/data"]

# Exponer puerto para distribuidores (6000)
EXPOSE 6000

# Ejecutar el Distribuidor con el driver en el classpath
CMD ["java", "-cp", ".:/app/sqlite-jdbc.jar", "Distribuidor"]
