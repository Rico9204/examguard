@echo off
rem Compile and run (Windows). ASCII only so it works in any console code page.
rem Uses java.exe from the SAME folder as javac.exe, so an older Java on PATH cannot break it.
cd /d "%~dp0"

set "JDKBIN="
for /f "delims=" %%i in ('where javac 2^>nul') do if not defined JDKBIN set "JDKBIN=%%~dpi"
if not defined JDKBIN (
  echo [ERROR] javac not found. Install JDK 11 or newer and add it to PATH.
  pause
  exit /b 1
)
echo Using JDK in: %JDKBIN%
"%JDKBIN%javac" -version

if exist out rmdir /s /q out
mkdir out
"%JDKBIN%javac" -encoding UTF-8 -d out src\examguard\*.java src\examguard\model\*.java src\examguard\fingerprint\*.java src\examguard\display\*.java src\examguard\analysis\*.java src\examguard\data\*.java src\examguard\exam\*.java src\examguard\ui\*.java src\examguard\ui\cbt\*.java
if errorlevel 1 (
  echo [ERROR] Compile failed. See messages above.
  pause
  exit /b 1
)

echo Starting... (keep this window open; errors will be shown here)
"%JDKBIN%java" -cp out examguard.Main
if errorlevel 1 (
  echo.
  echo [ERROR] The program stopped with an error. Copy the messages above.
  pause
)
