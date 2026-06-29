FROM dhi.io/eclipse-temurin:25-alpine3.23@sha256:6e8460b64ed88f558f1d59ffb2378716d7795e3b08ac1af98e0343b5b0e5382b

ARG project_version_arg
ENV PROJECT_VERSION=$project_version_arg
EXPOSE 8080

USER nonroot
COPY --chown=nonroot:nonroot arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
