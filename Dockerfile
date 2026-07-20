FROM dhi.io/eclipse-temurin:26-alpine3.23@sha256:fe31cc53e56ee9dd3ca1268fca813e4bdcad663f0b87a7ab31280f5b12b08e1e

ARG project_version_arg
ENV PROJECT_VERSION=$project_version_arg
EXPOSE 8080

USER nonroot
COPY --chown=nonroot:nonroot arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
