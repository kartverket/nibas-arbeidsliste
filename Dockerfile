FROM gcr.io/distroless/java25-debian13:nonroot@sha256:66a23001a1a4a5d02098c59fa27d265a8bb8e7d77d64464798e200441e53d040
ARG project_version_arg

ENV PROJECT_VERSION=$project_version_arg

EXPOSE 8080

COPY arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
