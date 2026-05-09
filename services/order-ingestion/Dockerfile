FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY target/order-ingestion-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
