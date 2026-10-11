@echo off
REM Compiles and starts the Adaptive Selective Repeat ARQ simulator (Swing GUI).
REM Needs a JDK (Java 8 or newer) with javac on the PATH.
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out -sourcepath src src\arq\Main.java
if errorlevel 1 (
    echo.
    echo Compilation failed. Make sure a JDK is installed and "javac" works in a terminal.
    pause
    exit /b 1
)
java -cp out arq.Main %*
