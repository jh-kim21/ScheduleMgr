#!/usr/bin/env bash
# 데스크톱 설치 파일을 생성합니다.
#   1) frontend를 빌드해 backend의 정적 리소스로 포함시키고
#   2) backend를 bootJar로 패키징한 뒤
#   3) jpackage로 OS별 설치 파일을 만듭니다.
#
# 사용법:
#   packaging/desktop/jpackage/build-desktop.sh              # OS 기본 설치 파일
#   APP_TYPE=app-image packaging/desktop/jpackage/...        # 설치 없이 실행할 앱 이미지
#   JPACKAGE=/path/to/jpackage packaging/desktop/jpackage/... # 특정 JDK의 jpackage 사용
#
# jpackage는 크로스 빌드를 못 합니다 — Windows .exe는 Windows에서, .deb는 Linux에서
# 이 스크립트를 그대로 실행해야 합니다.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_DIR="$ROOT_DIR/backend"
STATIC_DIR="$BACKEND_DIR/src/main/resources/static"
OUTPUT_DIR="$ROOT_DIR/packaging/desktop/jpackage/dist"

APP_NAME="ProjectFlow"
# 설치 파일 버전은 Gradle의 프로젝트 버전(0.0.1-SNAPSHOT)과 별개로 둔다. macOS는
# CFBundleVersion의 첫 숫자가 0이면 번들 생성을 거부하고, 설치 파일에는 SNAPSHOT 같은
# 접미사도 넣을 수 없다. 릴리스할 때 APP_VERSION으로 올린다.
APP_VERSION="${APP_VERSION:-1.0.0}"

# 설치 파일 종류. app-image는 설치 파일이 아니라 실행 가능한 앱 폴더다.
if [[ -z "${APP_TYPE:-}" ]]; then
  case "$(uname -s)" in
    Darwin) APP_TYPE="dmg" ;;
    Linux) APP_TYPE="deb" ;;
    MINGW* | MSYS* | CYGWIN*) APP_TYPE="exe" ;;
    *) APP_TYPE="app-image" ;;
  esac
fi

# jpackage는 자신이 속한 JDK의 런타임을 앱에 함께 넣는다. 그래서 앱을 컴파일한 툴체인과
# 같은 버전(build.gradle의 Java 21)을 쓰는 것이 안전하다 — PATH의 jpackage가 다른
# 메이저 버전이면 검증하지 않은 런타임 위에서 도는 앱이 만들어진다.
if [[ -z "${JPACKAGE:-}" ]]; then
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/jpackage" ]]; then
    JPACKAGE="$JAVA_HOME/bin/jpackage"
  else
    JPACKAGE="$(find "$HOME/.gradle/jdks" -name 'jpackage*' -type f -path '*/bin/*' 2>/dev/null | head -1 || true)"
    [[ -n "$JPACKAGE" ]] || JPACKAGE="$(command -v jpackage || true)"
  fi
fi
if [[ -z "$JPACKAGE" ]]; then
  echo "jpackage를 찾을 수 없습니다. JDK 21 이상을 설치하고 JAVA_HOME 또는 JPACKAGE를 지정하세요." >&2
  exit 1
fi
echo "jpackage: $JPACKAGE"
"$JPACKAGE" --version

case "$(uname -s)" in
  MINGW* | MSYS* | CYGWIN*)
    # jpackage의 exe/msi 생성은 WiX Toolset 3.x(candle.exe/light.exe)에 의존한다.
    # 없으면 "Can not find WiX tools" 류의 메시지만 나오므로 미리 확인한다. WiX 4/5는
    # JDK 21의 jpackage가 쓰지 못한다.
    if ! command -v candle > /dev/null 2>&1 && ! command -v candle.exe > /dev/null 2>&1; then
      echo "WiX Toolset 3.x이 PATH에 없습니다. exe/msi 생성에 필요합니다." >&2
      echo "  choco install wixtoolset  또는 https://github.com/wixtoolset/wix3/releases" >&2
      echo "  설치 없이 진행하려면 APP_TYPE=app-image 로 실행하세요." >&2
      exit 1
    fi
    ;;
esac

echo "[1/4] 프론트엔드 빌드"
(cd "$FRONTEND_DIR" && npm install && npm run build)

echo "[2/4] 프론트엔드 산출물을 backend 정적 리소스로 복사"
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -r "$FRONTEND_DIR"/dist/* "$STATIC_DIR"/

echo "[3/4] backend bootJar 빌드"
(cd "$BACKEND_DIR" && ./gradlew clean bootJar)

# 이름을 박아두면 버전을 올릴 때마다 어긋난다. -plain.jar 은 의존성이 없어 실행 불가라 제외.
MAIN_JAR_NAME="$(cd "$BACKEND_DIR/build/libs" && ls -- *.jar | grep -v -- '-plain\.jar$' | head -1)"
if [[ -z "$MAIN_JAR_NAME" ]]; then
  echo "실행 가능한 jar를 찾을 수 없습니다: $BACKEND_DIR/build/libs" >&2
  exit 1
fi
echo "main jar: $MAIN_JAR_NAME"

PLATFORM_OPTS=()
case "$(uname -s)" in
  Darwin)
    # 지정하지 않으면 jpackage가 메인 클래스의 패키지
    # (org.springframework.boot.loader.launch)를 번들 식별자로 써 버린다 — 앱을 스프링
    # 런처로 오인하게 만드는 값이다.
    PLATFORM_OPTS+=(--mac-package-identifier com.projectflow.desktop)
    ;;
  MINGW* | MSYS* | CYGWIN*)
    # --win-upgrade-uuid는 절대 바꾸지 마세요. 이 값이 바뀌면 새 버전이 기존 설치를
    # 덮어쓰지 않고 나란히 설치됩니다.
    PLATFORM_OPTS+=(
      --win-upgrade-uuid ced97718-1b4e-41bf-9cef-25f55252d541
      --win-menu               # 시작 메뉴 등록
      --win-menu-group "ProjectFlow"
      --win-shortcut           # 바탕화면 바로가기
      --win-dir-chooser        # 설치 위치 선택 허용
      --win-per-user-install   # 관리자 권한 없이 설치 (사내 배포에 적합)
    )
    ;;
esac

echo "[4/4] jpackage로 $APP_TYPE 생성"
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# jpackage는 --input 폴더를 통째로 앱에 넣는다. -plain.jar까지 들어가면 앱 크기만 커지므로
# 실행할 jar 하나만 담은 폴더를 따로 만든다.
STAGE_DIR="$(mktemp -d)"
trap 'rm -rf "$STAGE_DIR"' EXIT
cp "$BACKEND_DIR/build/libs/$MAIN_JAR_NAME" "$STAGE_DIR/"

# Git Bash의 jpackage는 Windows 네이티브 실행 파일이라 /tmp/... 같은 MSYS 경로를 열지
# 못한다. 인자로 넘기기 전에 Windows 경로로 바꾼다.
to_native_path() {
  if command -v cygpath > /dev/null 2>&1; then
    cygpath -w "$1"
  else
    printf '%s' "$1"
  fi
}
STAGE_ARG="$(to_native_path "$STAGE_DIR")"
DEST_ARG="$(to_native_path "$OUTPUT_DIR")"

"$JPACKAGE" \
  --type "$APP_TYPE" \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --input "$STAGE_ARG" \
  --main-jar "$MAIN_JAR_NAME" \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --java-options "-Dspring.profiles.active=desktop" \
  --java-options "-Dproject-flow.desktop.enabled=true" \
  --java-options "-Djava.awt.headless=false" \
  --dest "$DEST_ARG" \
  "${PLATFORM_OPTS[@]}"

echo
echo "완료:"
ls -1 "$OUTPUT_DIR"
