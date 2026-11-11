# STAGE 1: Build ung dung
FROM gradle:7.6-jdk17 AS builder

WORKDIR /home/gradle/project
COPY . .
RUN chmod +x gradlew
RUN ./gradlew clean build -x test

# STAGE 2: Chay ung dung
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /home/gradle/project/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "app.jar" ]