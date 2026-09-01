; ProjectFlow Windows 설치 프로그램.
;
; 관리자 권한 없이 사용자 폴더에 설치한다(사내 배포에 적합). 번들 런타임을 함께 넣으므로
; 받는 쪽에 Java가 없어도 된다.

Unicode true

!define APP_NAME "ProjectFlow"
!define APP_PUBLISHER "ProjectFlow"
!define APP_EXE "ProjectFlow.exe"
; 제거 프로그램 목록의 키. 버전이 올라가도 같은 값이어야 새 버전이 기존 설치를 대체한다.
!define UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\ProjectFlow"

Name "${APP_NAME} ${APP_VERSION}"
OutFile "${OUT_FILE}"
; 관리자 권한을 요구하지 않는다. Program Files 대신 사용자 폴더에 설치한다.
RequestExecutionLevel user
InstallDir "$LOCALAPPDATA\Programs\${APP_NAME}"
; 이미 설치돼 있으면 그 위치를 기본값으로 삼아 두 벌이 생기지 않게 한다.
InstallDirRegKey HKCU "Software\${APP_NAME}" "InstallDir"
SetCompressor /SOLID lzma

!include "MUI2.nsh"

!define MUI_ABORTWARNING
!define MUI_FINISHPAGE_RUN "$INSTDIR\${APP_EXE}"
!define MUI_FINISHPAGE_RUN_TEXT "ProjectFlow 실행"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "Korean"
!insertmacro MUI_LANGUAGE "English"

Section "ProjectFlow" SecMain
  SetOutPath "$INSTDIR"

  ; 런처, fat jar, 번들 런타임. stage/ 는 build-windows.sh 가 조립한다.
  File "stage\${APP_EXE}"
  SetOutPath "$INSTDIR\app"
  File /r "stage\app\*"
  SetOutPath "$INSTDIR\runtime"
  File /r "stage\runtime\*"

  SetOutPath "$INSTDIR"
  WriteUninstaller "$INSTDIR\uninstall.exe"

  WriteRegStr HKCU "Software\${APP_NAME}" "InstallDir" "$INSTDIR"

  ; 제어판 > 프로그램 추가/제거 등록. per-user 설치이므로 HKCU 에 쓴다.
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayName" "${APP_NAME}"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKCU "${UNINST_KEY}" "Publisher" "${APP_PUBLISHER}"
  WriteRegStr HKCU "${UNINST_KEY}" "DisplayIcon" "$INSTDIR\${APP_EXE}"
  WriteRegStr HKCU "${UNINST_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${UNINST_KEY}" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegStr HKCU "${UNINST_KEY}" "QuietUninstallString" '"$INSTDIR\uninstall.exe" /S'
  WriteRegDWORD HKCU "${UNINST_KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${UNINST_KEY}" "NoRepair" 1

  CreateDirectory "$SMPROGRAMS\${APP_NAME}"
  CreateShortcut "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}"
  CreateShortcut "$SMPROGRAMS\${APP_NAME}\${APP_NAME} 제거.lnk" "$INSTDIR\uninstall.exe"
  CreateShortcut "$DESKTOP\${APP_NAME}.lnk" "$INSTDIR\${APP_EXE}"
SectionEnd

Section "Uninstall"
  ; 설치한 것만 지운다. 사용자 데이터(%USERPROFILE%\.project-flow)는 건드리지 않는다 —
  ; 재설치할 때 프로젝트 기록이 사라지면 안 된다.
  Delete "$DESKTOP\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME}.lnk"
  Delete "$SMPROGRAMS\${APP_NAME}\${APP_NAME} 제거.lnk"
  RMDir "$SMPROGRAMS\${APP_NAME}"

  RMDir /r "$INSTDIR\runtime"
  RMDir /r "$INSTDIR\app"
  Delete "$INSTDIR\${APP_EXE}"
  Delete "$INSTDIR\uninstall.exe"
  RMDir "$INSTDIR"

  DeleteRegKey HKCU "${UNINST_KEY}"
  DeleteRegKey HKCU "Software\${APP_NAME}"
SectionEnd
