@echo off
setlocal

REM -----------------------------
REM Build (one machine)
REM -----------------------------
echo Compiling Java sources...
javac *.java
if errorlevel 1 (
	echo Compile failed. Fix errors and run again.
	exit /b 1
)

REM -----------------------------
REM One-PC configuration
REM -----------------------------
set NUM_WORKERS=3
set MASTER_PORT=6000
set REDUCER_HOST=127.0.0.1
set REDUCER_PORT=6100
set SRG_PORT=7000
set SRG_HOST=127.0.0.1
set WORKER_ENDPOINTS=127.0.0.1:5000,127.0.0.1:5001,127.0.0.1:5002

REM -----------------------------
REM Start backend services
REM -----------------------------
start "SRG" cmd /k java -cp . SecureRandomServer %SRG_PORT%
start "Reducer" cmd /k java -cp . Reducer %REDUCER_PORT%

timeout /t 2 >nul

start "Worker-0" cmd /k java -cp . Worker 5000 %SRG_HOST% %SRG_PORT%
start "Worker-1" cmd /k java -cp . Worker 5001 %SRG_HOST% %SRG_PORT%
start "Worker-2" cmd /k java -cp . Worker 5002 %SRG_HOST% %SRG_PORT%

timeout /t 2 >nul

start "Master" cmd /k java -cp . Master %NUM_WORKERS% %MASTER_PORT% %REDUCER_HOST% %REDUCER_PORT% %WORKER_ENDPOINTS%

echo.
echo System started.
echo Manager Console CMD:
echo   java -cp . ManagerConsole 127.0.0.1 %MASTER_PORT%
echo.
echo Player Console CMD:
echo   java -cp . DummyPlayerConsole 127.0.0.1 %MASTER_PORT%

endlocal