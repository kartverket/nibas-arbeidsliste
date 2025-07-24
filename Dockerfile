FROM eclipse-temurin:24-jdk-alpine@sha256:da2196e2ec0d51d8c4c88c19f671952d251dd512c19381d5f6eab6d97dbd5908
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
