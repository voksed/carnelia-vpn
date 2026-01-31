@echo off
setlocal enabledelayedexpansion

set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-11.0.29.7-hotspot
set ANDROID_HOME=C:\Users\aslan\AppData\Local\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\build-tools\33.0.2;%PATH%

cd /d D:\carneliavpn\carnelia-vpn

echo Building Carnelia VPN...
echo.

REM Copy gradle wrapper jar manually
if not exist gradle\wrapper\gradle-wrapper.jar (
    echo Downloading gradle...
    powershell -Command "Invoke-WebRequest -Uri 'https://repo.gradle.org/gradle/gradle-8.6-all.zip' -OutFile 'gradle-8.6-all.zip' -TimeoutSec 300"
    powershell -Command "Expand-Archive -Path 'gradle-8.6-all.zip' -DestinationPath '.' -Force"
    move gradle-8.6 gradle-home
    copy gradle-home\lib\gradle-wrapper.jar gradle\wrapper\gradle-wrapper.jar
)

REM Build APK
echo Cleaning build...
call gradlew.bat clean

echo Assembling debug APK...
call gradlew.bat assembleDebug

if exist build\outputs\apk\debug\carnelia-vpn-debug.apk (
    echo.
    echo SUCCESS! APK created:
    echo %CD%\build\outputs\apk\debug\carnelia-vpn-debug.apk
    echo.
    dir build\outputs\apk\debug\*.apk
) else (
    echo Build failed!
    exit /b 1
)

endlocal
