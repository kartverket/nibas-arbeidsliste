FROM dhi.io/eclipse-temurin:26-alpine3.23@sha256:135b10e246dc1939df08c1706a4def28a793c2bfa99311f747d00ab40b68426e

ARG project_version_arg
ENV PROJECT_VERSION=$project_version_arg
EXPOSE 8080

USER nonroot
COPY --chown=nonroot:nonroot arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
