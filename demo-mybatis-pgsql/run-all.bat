@echo off
start "backend"  cmd /k "%~dp0run-backend.bat"
start "frontend" cmd /k "%~dp0run-frontend.bat"
