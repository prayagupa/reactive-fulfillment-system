FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY target/pick-engine-*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
