; ProjectFlow.exe — 앱을 시작하는 런처 스텁.
;
; jpackage가 만들어 주던 실행 파일에 해당한다. jpackage는 크로스 빌드를 못 하므로
; (macOS에서 --type exe는 거부된다) 이 스텁을 NSIS로 대신 만든다. NSIS는 macOS에서도
; Windows 실행 파일을 컴파일한다.
;
; 하는 일은 하나다: 옆에 있는 번들 런타임으로 fat jar를 실행한다. javaw.exe를 쓰므로
; 콘솔 창이 뜨지 않고, 기동이 끝나면 앱이 스스로 기본 브라우저를 연다
; (DesktopBrowserLauncher).

Name "ProjectFlow"
OutFile "stage\ProjectFlow.exe"
Unicode true

; 설치 프로그램이 아니라 런처다. UI 없이 섹션만 실행하고 끝낸다.
SilentInstall silent
RequestExecutionLevel user

Section "Run"
  ; $EXEDIR = 이 exe가 놓인 폴더. 설치 위치가 어디든 상대 경로로 찾는다.
  SetOutPath "$EXEDIR"
  Exec '"$EXEDIR\runtime\bin\javaw.exe" -XX:MaxRAMPercentage=70 -Dspring.profiles.active=desktop -Dproject-flow.desktop.enabled=true -Djava.awt.headless=false -jar "$EXEDIR\app\@MAIN_JAR@"'
SectionEnd
