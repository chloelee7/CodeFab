@echo off
chcp 65001 >nul
REM factory.bat - CodeFab interpreter runner wrapper
REM Builds first if build/install/factory/bin/factory.bat does not exist.
SET SCRIPT_DIR=%~dp0
SET BINARY=%SCRIPT_DIR%build\install\factory\bin\factory.bat

IF NOT EXIST "%BINARY%" (
  echo [factory] Binary not found. Running build first...
  CALL "%SCRIPT_DIR%gradlew.bat" installDist -q
  IF ERRORLEVEL 1 (
    echo [factory] Build failed. Check JAVA_HOME and try again.
    pause
    EXIT /B 1
  )
)

CALL "%BINARY%" %*

