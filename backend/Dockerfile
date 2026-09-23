FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21.0.5_11-jre-alpine
RUN apk add --no-cache wget \
    && addgroup -S creditengine \
    && adduser -S -G creditengine creditengine
WORKDIR /app
COPY --from=build --chown=creditengine:creditengine /workspace/target/credit-engine-0.0.1-SNAPSHOT.jar app.jar
USER creditengine
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
