@echo off
title Crypto Shop - Bot + Dashboard
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%USERPROFILE%\tools\apache-maven-3.9.9\bin;%PATH%"

rem .env laden (Zeilen mit # werden ignoriert)
if exist "%~dp0.env" (
    for /f "usebackq eol=# tokens=1,* delims==" %%a in ("%~dp0.env") do set "%%a=%%b"
)

set SPRING_PROFILES_ACTIVE=local
cd /d "%~dp0backend"
echo.
echo  Starte Shop... Dashboard: http://localhost:8080
echo  Zum Beenden dieses Fenster schliessen oder STRG+C druecken.
echo.
mvn -q -DskipTests spring-boot:run
pause
