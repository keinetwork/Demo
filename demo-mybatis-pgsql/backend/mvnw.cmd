@echo off
setlocal enabledelayedexpansion

set MAVEN_VERSION=3.9.9
set DISTS_DIR=%USERPROFILE%\.m2\wrapper\dists
set MAVEN_HOME=%DISTS_DIR%\apache-maven-%MAVEN_VERSION%
set MAVEN_ZIP=%DISTS_DIR%\apache-maven-%MAVEN_VERSION%-bin.zip
set MAVEN_URL=https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip

where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
    mvn %*
    exit /b %ERRORLEVEL%
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [mvnw] Maven not found on PATH. Downloading Maven %MAVEN_VERSION% ...
    if not exist "%DISTS_DIR%" mkdir "%DISTS_DIR%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%MAVEN_ZIP%'"
    if errorlevel 1 (
        echo [mvnw] Download failed. Install Maven manually or check your internet connection.
        exit /b 1
    )
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%MAVEN_ZIP%' -DestinationPath '%DISTS_DIR%' -Force"
    del "%MAVEN_ZIP%"
)

"%MAVEN_HOME%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
