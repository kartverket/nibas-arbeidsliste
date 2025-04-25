FROM eclipse-temurin:21-jre-alpine@sha256:8728e354e012e18310faa7f364d00185277dec741f4f6d593af6c61fc0eb15fd
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
COPY build/libs/*.jar app.jar

VOLUME /tmp

USER ${USER_ID}

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
