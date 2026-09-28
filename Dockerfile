# The build stage runs on the builder's native platform. A jar is
# platform-neutral, so CI can build an arm64 image for Graviton without
# emulating Maven. Only the runtime stage uses the target platform.
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencies get their own layer, so a code-only change skips the download.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
