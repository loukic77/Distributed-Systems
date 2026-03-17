@echo off
start java Worker 5000
start java Worker 5001
start java Worker 5002
timeout /t 2
java Master 3

REM THIS IS FOR CONVINIENCE , compile java files , then run run.bat  