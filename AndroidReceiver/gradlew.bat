@echo off
REM Gradle wrapper batch script for Windows

cd /d "%~dp0"

REM Check if gradle wrapper exists
if not exist "gradle\wrapper\gradle.exe" (
    echo Gradle wrapper not found. Download Gradle from https://gradle.org/install/
    exit /b 1
)

REM Run Gradle
"gradle\wrapper\gradle" %*
exit /b %errorlevel%
REM Gradle wrapper batch script for Windows

cd /d "%~dp0"

REM Check if gradle wrapper exists
if not exist "gradle\wrapper\gradle.exe" (
    echo Gradle wrapper not found. Download Gradle from https://gradle.org/install/
    exit /b 1
)

REM Run Gradle
"gradle\wrapper\gradle" %*
exit /b %errorlevel%