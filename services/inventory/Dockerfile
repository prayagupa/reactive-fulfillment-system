FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY target/inventory-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
