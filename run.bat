@echo off
rem Builds RentNGo and starts it. Works from any folder: double-click it, or in a terminal run  .\run.bat
rem (inside the RentNGo folder) or  C:\Users\johnp\Documents\RentNGo\run.bat  from anywhere else.
setlocal
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo javac was not found. Install a JDK ^(Java 17 or newer^) and make sure it is on PATH.
    pause
    exit /b 1
)

echo Compiling RentNGo...
if exist out\sources.txt del out\sources.txt
if not exist out mkdir out
dir /s /b src\*.java > out\sources.txt
javac -encoding UTF-8 -cp "lib/*" -d out @out\sources.txt
if errorlevel 1 (
    echo.
    echo Compile failed - see the errors above.
    pause
    exit /b 1
)
del out\sources.txt
xcopy /e /i /y /q src\resources out\resources >nul

echo Starting RentNGo...
java --enable-native-access=ALL-UNNAMED -cp "out;lib/*" app.RentNGo


/Why need it: RentNGo have many files and libraries. Normal VS Code Run compile one file only, so it fail. That the “package gui does not exist” error from before. Now VS Code Run button call run.bat.