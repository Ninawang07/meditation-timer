@echo off
set TOOLS=C:\apk工具
set JAVA_HOME=%TOOLS%\jdk
set ANDROID_SDK_ROOT=%TOOLS%\android-sdk
set PATH=%JAVA_HOME%\bin;%PATH%

cd /d "%~dp0android"
%TOOLS%\gradle-8.14.3\bin\gradle.bat assembleDebug
