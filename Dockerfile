FROM dhi.io/eclipse-temurin:26-alpine3.23@sha256:5d6b41e324f47216ab8fd6074e567ccb6ff7711998a3c99cc0d22bc3b0ff62a2

ARG project_version_arg
ENV PROJECT_VERSION=$project_version_arg
EXPOSE 8080

USER nonroot
COPY --chown=nonroot:nonroot arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
