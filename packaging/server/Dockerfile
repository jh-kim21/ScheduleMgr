# 서버 배포용 멀티스테이지 빌드
# 빌드: docker build -f packaging/server/Dockerfile -t project-flow-backend .
# 실행: docker run -p 8080:8080 --env-file .env project-flow-backend
#
# 이 이미지는 백엔드만 담는다. 프론트엔드를 다른 오리진에서 서비스한다면
# CORS_ALLOWED_ORIGINS 에 그 오리진을 지정해야 한다 (미지정 시 교차 오리진 요청 거부).

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
