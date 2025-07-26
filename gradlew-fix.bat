@echo off
REM Custom Gradle wrapper that fixes JAVA_HOME for this project
REM Usage: gradlew-fix.bat [gradle commands...]

REM Set correct JAVA_HOME for this project
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr

REM Call original gradlew with all arguments
call "%~dp0gradlew.bat" %*