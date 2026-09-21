FROM dhi.io/eclipse-temurin:26-alpine3.23@sha256:86f11ff533f5481cac607f2ec67474806c7d6e00ae87d6ed0b121ecccfb3ee39

ARG project_version_arg
ENV PROJECT_VERSION=$project_version_arg
EXPOSE 8080

USER nonroot
COPY --chown=nonroot:nonroot arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
