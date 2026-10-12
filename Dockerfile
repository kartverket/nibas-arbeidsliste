FROM dhi.io/eclipse-temurin:26-alpine3.23@sha256:d4bb66dd634d393064fdc5f6c4a290f0107c457106cae8b57f99b1aa7c8228f5

ARG project_version_arg
ENV PROJECT_VERSION=$project_version_arg
EXPOSE 8080

USER nonroot
COPY --chown=nonroot:nonroot arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
