#!/usr/bin/env bash
# Windows x64 설치 파일을 만듭니다. macOS/Linux에서도 동작합니다.
#
# 왜 jpackage가 아닌가: jpackage는 크로스 빌드를 못 합니다 — macOS의 jpackage는
# app-image/dmg/pkg만 지원하고 --type exe는 "Invalid or unsupported type"으로 거부합니다.
# 반면 설치 파일 자체는 jpackage 없이도 만들 수 있습니다. NSIS는 macOS에서 Windows
# 실행 파일을 컴파일하고, Windows용 런타임은 내려받아 함께 넣으면 됩니다.
# Windows 장비에서 만들 때는 packaging/desktop/jpackage/build-desktop.sh 도 쓸 수 있습니다.
#
# 준비물: makensis (brew install makensis), node, JDK 21
# 사용법: packaging/desktop/nsis/build-windows.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
NSIS_DIR="$ROOT_DIR/packaging/desktop/nsis"
FRONTEND_DIR="$ROOT_DIR/frontend"
BACKEND_DIR="$ROOT_DIR/backend"
STATIC_DIR="$BACKEND_DIR/src/main/resources/static"
CACHE_DIR="$NSIS_DIR/.cache"
STAGE_DIR="$NSIS_DIR/stage"
OUTPUT_DIR="$NSIS_DIR/dist"

APP_NAME="ProjectFlow"
# macOS 쪽과 같은 이유로 프로젝트 버전(0.0.1-SNAPSHOT)과 분리한다. 설치 파일 버전에는
# SNAPSHOT 같은 접미사를 넣을 수 없다.
APP_VERSION="${APP_VERSION:-1.0.0}"

# 앱을 컴파일한 툴체인(build.gradle의 Java 21)과 같은 메이저 버전을 번들한다.
JRE_VERSION="21.0.12.1_1"
JRE_ZIP="OpenJDK21U-jre_x64_windows_hotspot_${JRE_VERSION}.zip"
JRE_URL="https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"
JRE_SHA256="d35f31e712f0fcf6ac5a093edc90204fbff22f720ba3950bd09d331d5e621636"

# 산출물 종류. installer는 makensis가 실제로 동작해야 하고, portable은 아무 도구도
# 필요하지 않다. 지정하지 않으면 makensis를 실제로 돌려보고 정한다 — 설치만 되어 있고
# 동작하지 않는 경우가 있어서(macOS 26/arm64의 3.12는 어떤 스크립트에서든
# std::bad_alloc으로 죽는다) `command -v` 확인만으로는 부족하다.
probe_makensis() {
  command -v makensis > /dev/null 2>&1 || return 1
  local probe_dir ok
  probe_dir="$(mktemp -d)"
  printf 'Name "p"\nOutFile "p.exe"\nSilentInstall silent\nSection "s"\nSectionEnd\n' > "$probe_dir/p.nsi"
  if (cd "$probe_dir" && makensis -V0 p.nsi > /dev/null 2>&1 && [[ -f p.exe ]]); then
    ok=0
  else
    ok=1
  fi
  rm -rf "$probe_dir"
  return $ok
}

if [[ -z "${OUTPUT:-}" ]]; then
  if probe_makensis; then
    OUTPUT="installer"
  else
    OUTPUT="portable"
    echo "알림: 동작하는 makensis가 없어 portable zip으로 만듭니다."
    echo "      .exe 설치 파일이 필요하면 Windows에서"
    echo "      packaging/desktop/jpackage/build-desktop.sh 를 실행하거나,"
    echo "      .github/workflows/desktop-installer.yml 을 Actions에서 실행하세요."
    echo
  fi
fi

echo "[1/6] Windows x64 런타임 준비"
mkdir -p "$CACHE_DIR"
if [[ ! -f "$CACHE_DIR/$JRE_ZIP" ]]; then
  echo "  내려받기: $JRE_ZIP"
  curl -fsSL -o "$CACHE_DIR/$JRE_ZIP" "$JRE_URL"
else
  echo "  캐시 사용: $JRE_ZIP"
fi
# 무엇이 앱에 들어가는지 확실히 하기 위해 매번 검증한다. 캐시가 오염됐을 수도 있다.
ACTUAL_SHA="$(shasum -a 256 "$CACHE_DIR/$JRE_ZIP" | awk '{print $1}')"
if [[ "$ACTUAL_SHA" != "$JRE_SHA256" ]]; then
  echo "  런타임 체크섬이 다릅니다. 기대 $JRE_SHA256, 실제 $ACTUAL_SHA" >&2
  exit 1
fi
echo "  SHA256 확인"

echo "[2/6] 프론트엔드 빌드"
(cd "$FRONTEND_DIR" && npm install && npm run build)

echo "[3/6] 프론트엔드 산출물을 backend 정적 리소스로 복사"
rm -rf "$STATIC_DIR"
mkdir -p "$STATIC_DIR"
cp -r "$FRONTEND_DIR"/dist/* "$STATIC_DIR"/

echo "[4/6] backend bootJar 빌드"
(cd "$BACKEND_DIR" && ./gradlew clean bootJar)
# 이름을 박아두면 버전을 올릴 때마다 어긋난다. -plain.jar 은 의존성이 없어 실행 불가라 제외.
MAIN_JAR="$(cd "$BACKEND_DIR/build/libs" && ls -- *.jar | grep -v -- '-plain\.jar$' | head -1)"
[[ -n "$MAIN_JAR" ]] || {
  echo "실행 가능한 jar를 찾을 수 없습니다." >&2
  exit 1
}
echo "  main jar: $MAIN_JAR"

echo "[5/6] 설치 트리 조립"
rm -rf "$STAGE_DIR"
mkdir -p "$STAGE_DIR/app" "$STAGE_DIR/runtime"
cp "$BACKEND_DIR/build/libs/$MAIN_JAR" "$STAGE_DIR/app/"

# zip 안에 jdk-...-jre/ 한 겹이 더 있으므로 그 아래 내용만 runtime/ 으로 옮긴다.
UNZIP_TMP="$(mktemp -d)"
trap 'rm -rf "$UNZIP_TMP"' EXIT
unzip -q "$CACHE_DIR/$JRE_ZIP" -d "$UNZIP_TMP"
JRE_ROOT="$(find "$UNZIP_TMP" -maxdepth 1 -mindepth 1 -type d | head -1)"
cp -R "$JRE_ROOT"/. "$STAGE_DIR/runtime/"
[[ -f "$STAGE_DIR/runtime/bin/javaw.exe" ]] || {
  echo "런타임에 javaw.exe가 없습니다: $STAGE_DIR/runtime/bin" >&2
  exit 1
}

# makensis는 UTF-8 스크립트에 BOM이 없으면 "Bad text encoding"으로 거부한다(주석이 한글).
# 소스는 BOM 없이 두고 컴파일용 사본에만 붙인다 — 저장소 파일에 BOM이 끼면 diff가 지저분해진다.
# 사본을 NSIS_DIR에 두는 이유: installer.nsi의 File "stage\..." 경로가 스크립트 위치 기준이다.
add_bom() {
  printf '\xEF\xBB\xBF' > "$2"
  cat "$1" >> "$2"
}

# 런처는 jar 이름을 알아야 하는데 NSIS에는 문자열 치환이 마땅치 않아 빌드 시점에 넣는다.
sed "s|@MAIN_JAR@|$MAIN_JAR|" "$NSIS_DIR/launcher.nsi" > "$STAGE_DIR/launcher.subst.nsi"
add_bom "$STAGE_DIR/launcher.subst.nsi" "$NSIS_DIR/launcher.generated.nsi"
add_bom "$NSIS_DIR/installer.nsi" "$NSIS_DIR/installer.generated.nsi"

if [[ "$OUTPUT" == "portable" ]]; then
  echo "[6/6] portable zip 패키징"
  mkdir -p "$OUTPUT_DIR"

  # 런처는 템플릿에서 만든다. Windows용 텍스트라 CRLF로 바꾸고, README는 메모장이
  # 한글을 제대로 읽도록 UTF-8 BOM을 붙인다.
  PORTABLE_DIR="$NSIS_DIR/portable"
  to_crlf() { sed 's/$/\r/' "$1" > "$2"; }

  sed "s|@MAIN_JAR@|$MAIN_JAR|" "$PORTABLE_DIR/ProjectFlow.vbs.in" > "$STAGE_DIR/.vbs.tmp"
  to_crlf "$STAGE_DIR/.vbs.tmp" "$STAGE_DIR/ProjectFlow.vbs"
  sed "s|@MAIN_JAR@|$MAIN_JAR|" "$PORTABLE_DIR/ProjectFlow.bat.in" > "$STAGE_DIR/.bat.tmp"
  to_crlf "$STAGE_DIR/.bat.tmp" "$STAGE_DIR/ProjectFlow.bat"
  sed "s|@APP_VERSION@|$APP_VERSION|" "$PORTABLE_DIR/README.txt.in" > "$STAGE_DIR/.readme.tmp"
  to_crlf "$STAGE_DIR/.readme.tmp" "$STAGE_DIR/.readme.crlf"
  printf '\xEF\xBB\xBF' > "$STAGE_DIR/README.txt"
  cat "$STAGE_DIR/.readme.crlf" >> "$STAGE_DIR/README.txt"
  rm -f "$STAGE_DIR"/.vbs.tmp "$STAGE_DIR"/.bat.tmp "$STAGE_DIR"/.readme.tmp "$STAGE_DIR"/.readme.crlf

  ZIP_NAME="${APP_NAME}-${APP_VERSION}-windows-x64.zip"
  rm -f "$OUTPUT_DIR/$ZIP_NAME"
  # zip 최상위를 ProjectFlow/ 한 겹으로 감싼다 — 압축을 풀 때 파일이 흩어지지 않게.
  PACK_TMP="$(mktemp -d)"
  mkdir -p "$PACK_TMP/$APP_NAME"
  cp -R "$STAGE_DIR"/. "$PACK_TMP/$APP_NAME/"
  rm -f "$PACK_TMP/$APP_NAME"/launcher.subst.nsi
  (cd "$PACK_TMP" && zip -qr "$OUTPUT_DIR/$ZIP_NAME" "$APP_NAME")
  rm -rf "$PACK_TMP"

  echo
  echo "완료:"
  ls -lh "$OUTPUT_DIR/$ZIP_NAME"
  exit 0
fi

echo "[6/6] NSIS 컴파일"
mkdir -p "$OUTPUT_DIR"
# 런처 스텁을 먼저 만들어 stage/ 에 두면, 설치 프로그램이 그것까지 함께 담는다.
(cd "$NSIS_DIR" && makensis -V2 launcher.generated.nsi)
[[ -f "$STAGE_DIR/$APP_NAME.exe" ]] || {
  echo "런처 생성 실패: $STAGE_DIR/$APP_NAME.exe" >&2
  exit 1
}

OUT_FILE="$OUTPUT_DIR/${APP_NAME}-${APP_VERSION}-setup.exe"
(cd "$NSIS_DIR" && makensis -V2 \
  -DAPP_VERSION="$APP_VERSION" \
  -DOUT_FILE="$OUT_FILE" \
  installer.generated.nsi)

echo
echo "완료:"
ls -lh "$OUT_FILE"
