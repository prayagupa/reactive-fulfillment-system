FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY target/carrier-tracking-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
