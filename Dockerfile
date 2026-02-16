FROM gcr.io/distroless/java25-debian13:nonroot@sha256:e1eeec12952b877762d2a0a94a216600b34056942f6d82635a1d4a5dc8ee9770
ARG project_version_arg

ENV PROJECT_VERSION=$project_version_arg

EXPOSE 8080

COPY arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
