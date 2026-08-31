#!/usr/bin/env bash
# 데스크톱 배포판(app image)을 생성합니다.
#   1) frontend를 빌드해 backend의 정적 리소스로 포함시키고
#   2) backend를 bootJar로 패키징한 뒤
#   3) jpackage로 OS별 앱 이미지를 만듭니다.
#
# 사용법: packaging/desktop/jpackage/build-desktop.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_DIR="$ROOT_DIR/backend"
STATIC_DIR="$BACKEND_DIR/src/main/resources/static"
OUTPUT_DIR="$ROOT_DIR/packaging/desktop/jpackage/dist"

APP_NAME="ProjectFlow"
APP_VERSION="0.0.1"
MAIN_JAR_NAME="project-flow-backend-0.0.1-SNAPSHOT.jar"

echo "[1/4] 프론트엔드 빌드"
(cd "$FRONTEND_DIR" && npm install && npm run build)

echo "[2/4] 프론트엔드 산출물을 backend 정적 리소스로 복사"
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -r "$FRONTEND_DIR"/dist/* "$STATIC_DIR"/

echo "[3/4] backend bootJar 빌드"
(cd "$BACKEND_DIR" && ./gradlew clean bootJar)

echo "[4/4] jpackage로 앱 이미지 생성"
mkdir -p "$OUTPUT_DIR"
jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --input "$BACKEND_DIR/build/libs" \
  --main-jar "$MAIN_JAR_NAME" \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --java-options "-Dspring.profiles.active=desktop" \
  --dest "$OUTPUT_DIR"

echo "완료: $OUTPUT_DIR/$APP_NAME"
