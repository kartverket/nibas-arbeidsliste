FROM eclipse-temurin:25-jdk-alpine@sha256:0c4c6300cc86efdf6454702336a0d60352e227f3a862e8ae9861f393f8f1ede9
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
