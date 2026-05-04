FROM gcr.io/distroless/java25-debian13:nonroot@sha256:b1eb8a18891104b7405f29edbb2eaca9b34179707957a0e5a41b54d4a45cbdfd
ARG project_version_arg

ENV PROJECT_VERSION=$project_version_arg

EXPOSE 8080

COPY arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
