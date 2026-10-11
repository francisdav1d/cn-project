@echo off
REM Runs the headless comparison: fixed window 4 / fixed window 16 / adaptive window
REM under good, moderate and poor networks. Optional arguments: [packets] [runs]
REM Example:  run-experiment.bat 500 30
cd /d "%~dp0"
if not exist out mkdir out
javac -encoding UTF-8 -d out -sourcepath src src\arq\Main.java
if errorlevel 1 (
    echo Compilation failed. Make sure a JDK is installed and "javac" works in a terminal.
    pause
    exit /b 1
)
java -cp out arq.Main --experiment %*
echo.
pause
