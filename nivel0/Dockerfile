FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

COPY Estanque.java .

RUN javac Estanque.java

# Crear directorio para datos persistentes
RUN mkdir -p /app/data

# Volumen para persistencia
VOLUME ["/app/data"]

EXPOSE 5000

CMD ["java", "Estanque"]
