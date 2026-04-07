FROM gcr.io/distroless/java25-debian13:nonroot@sha256:efb9a5000ce8ff56745d56c88c8e61017e674ed053b67e0c44af25ddabf1faa8
ARG project_version_arg

ENV PROJECT_VERSION=$project_version_arg

EXPOSE 8080

COPY arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
