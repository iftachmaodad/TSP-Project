@echo off
setlocal enabledelayedexpansion
title TSP Solver Launcher

cd /d "%~dp0"

if not exist pom.xml (
    echo.
    echo  [ERROR] pom.xml not found in %~dp0
    echo  Make sure run.bat is placed in the project root, next to pom.xml.
    echo.
    pause
    exit /b 1
)

:MENU
cls
echo.
echo  ================================================
echo   TSP Solver -- Launcher
echo  ================================================
echo.
echo   [1]  Run Application
echo   [2]  Run Tests
echo   [3]  Run Benchmark (console output)
echo   [4]  Run Application + Tests
echo   [5]  Clean and Rebuild
echo   [0]  Exit
echo.
echo  ================================================
set /p CHOICE="  Choose an option: "

if "%CHOICE%"=="1" goto RUN_APP
if "%CHOICE%"=="2" goto RUN_TESTS
if "%CHOICE%"=="3" goto RUN_BENCHMARK
if "%CHOICE%"=="4" goto RUN_APP_AND_TESTS
if "%CHOICE%"=="5" goto CLEAN_BUILD
if "%CHOICE%"=="0" goto EXIT

echo.
echo  Invalid option. Try again.
pause
goto MENU

:: ─────────────────────────────────────────────
:RUN_APP
cls
echo.
echo  Launching TSP Solver...
echo.
:: Use PowerShell to start mvn as a fully detached process with no window,
:: then close this launcher immediately.
powershell -WindowStyle Hidden -Command "Start-Process 'mvn' -ArgumentList 'javafx:run' -WorkingDirectory '%cd%' -WindowStyle Hidden"
exit /b 0

:: ─────────────────────────────────────────────
:RUN_TESTS
cls
echo.
echo  Running tests...
echo.
call mvn test
if %ERRORLEVEL% equ 0 (
    echo.
    echo  [OK] All tests passed.
) else (
    echo.
    echo  [FAIL] Some tests failed. See output above.
)
pause
goto MENU

:: ─────────────────────────────────────────────
:RUN_BENCHMARK
cls
echo.
echo  Running Benchmark (all 4 solvers x 8 instances)...
echo.
call mvn exec:java -Dexec.mainClass="benchmark.SolverBenchmark" 2>&1
echo.
echo  ================================================
echo   Benchmark complete. Press any key to go back.
echo  ================================================
pause >nul
goto MENU

:: ─────────────────────────────────────────────
:RUN_APP_AND_TESTS
cls
echo.
echo  Running tests first...
echo.
call mvn test
if %ERRORLEVEL% neq 0 (
    echo.
    echo  [FAIL] Tests failed -- aborting app launch.
    pause
    goto MENU
)
echo.
echo  All tests passed. Launching application...
echo.
powershell -WindowStyle Hidden -Command "Start-Process 'mvn' -ArgumentList 'javafx:run' -WorkingDirectory '%cd%' -WindowStyle Hidden"
exit /b 0

:: ─────────────────────────────────────────────
:CLEAN_BUILD
cls
echo.
echo  Cleaning and rebuilding...
echo.
call mvn clean compile
if %ERRORLEVEL% equ 0 (
    echo.
    echo  [OK] Build successful.
) else (
    echo.
    echo  [ERROR] Build failed. See output above.
)
pause
goto MENU

:: ─────────────────────────────────────────────
:EXIT
cls
echo.
echo  Goodbye.
echo.
exit /b 0
