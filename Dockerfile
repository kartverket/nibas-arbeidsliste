FROM gcr.io/distroless/java25-debian13:nonroot@sha256:2e3d8fc12381605a522cb015f551cb340fd857afb51c71d7decf4fea0d8802d5
ARG project_version_arg

ENV PROJECT_VERSION=$project_version_arg

EXPOSE 8080

COPY arbeidsliste-api/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Duser.timezone=Europe/Oslo", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app.jar"]
