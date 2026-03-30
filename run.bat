@echo off

start java SecureRandomServer 7000
start java Reducer 6100
timeout /t 2
start java Worker 5000 127.0.0.1 7000
start java Worker 5001 127.0.0.1 7000
start java Worker 5002 127.0.0.1 7000
timeout /t 2
start cmd /k java Master 3 6000 127.0.0.1 6100
REM THIS IS FOR CONVINIENCE , compile java files , then run run.bat  