FROM dhi.io/eclipse-temurin:25-alpine3.23@sha256:b854ef99193c77ca1c6655c813ca57ab55c0fb0f0f4307511c8889045d0d5a19

ARG project_version_arg
ENV PROJECT_VERSION=$project_version_arg
EXPOSE 8080

USER nonroot
COPY --chown=nonroot:nonroot arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
