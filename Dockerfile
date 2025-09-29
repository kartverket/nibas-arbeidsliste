FROM eclipse-temurin:25-jdk-alpine@sha256:f4c0b771cfed29902e1dd2e5c183b9feca633c7686fb85e278a0486b03d27369
ARG project_version_arg

ENV PROJECT_VERSION=$project_version_arg
ENV GROUP_NAME=nibas
ENV GROUP_ID=199
ENV USER_NAME=nibas-arbeidsliste
ENV USER_ID=199

RUN addgroup -g ${GROUP_ID} ${GROUP_NAME} && adduser --uid ${USER_ID} --disabled-password --gecos '' ${USER_NAME} --ingroup ${GROUP_NAME}

RUN apk add --no-cache tzdata
ENV TZ=Europe/Oslo

EXPOSE 8080

WORKDIR /app

COPY arbeidsliste-api/build/libs/*.jar app.jar

VOLUME /tmp

USER ${USER_ID}

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
