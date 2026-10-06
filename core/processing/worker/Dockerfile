ARG BASE_IMAGE
FROM ${BASE_IMAGE}
VOLUME /tmp

# libgcc needed on eclipse-temurin:8-jdk-alpine to workaround https://github.com/adoptium/temurin-build/issues/3002
RUN apk update && apk add libgcc=15.2.0-r5

ARG JAR_FILE
ADD ${JAR_FILE} app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]