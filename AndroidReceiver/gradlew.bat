@echo off
cd /d "%~dp0"

set APP_HOME=%CD%
set APP_BASE_NAME=%~n0

if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo Error: gradle-wrapper.jar not found. Run gradlew wrapper --gradle-version 8.5
    exit /b 1
)

set DEFAULT_JVM_OPTS=-Xmx512m -Xms256m
set JAVA_OPTS=
set GRADLE_OPTS=

set CLASSPATH=gradle\wrapper\gradle-wrapper.jar

REM Use JDK 11 for Android Gradle Plugin compatibility
set JAVA_HOME=C:\Program Files\Microsoft\jdk-11.0.33.101-hotspot

java %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %errorlevel%