# 서버 배포용 멀티스테이지 빌드
# 빌드: docker build -f packaging/server/Dockerfile -t project-flow-backend .
# 실행: docker run -p 8080:8080 --env-file .env project-flow-backend

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY backend/gradlew backend/gradlew
COPY backend/gradle backend/gradle
COPY backend/build.gradle backend/settings.gradle backend/
WORKDIR /workspace/backend
RUN ./gradlew --version
COPY backend/src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=server
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
