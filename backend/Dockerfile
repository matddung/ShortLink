FROM gradle:8.10.2-jdk17 AS build
WORKDIR /workspace
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
COPY src ./src
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]