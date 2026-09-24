@echo off
rem Compila el backend (Java) y arranca el servidor del juego. Abre el navegador automaticamente.
rem Uso: doble clic, o  iniciar_juego.bat --sin-navegador
setlocal
title Tower Defense - Servidor del juego
cd /d "%~dp0backend"

echo Compilando el backend...
if not exist out mkdir out
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 (
  echo.
  echo *** Error al compilar. Verifica que tienes el JDK 17 o superior ^(comando javac^). ***
  pause
  exit /b 1
)

set ABRIR=1
if /I "%~1"=="--sin-navegador" set ABRIR=0
if "%ABRIR%"=="1" start "" cmd /c "timeout /t 2 /nobreak >nul & start http://127.0.0.1:8080/"

echo.
echo Juego en http://127.0.0.1:8080/   ^(cierra esta ventana o pulsa Ctrl+C para detenerlo^)
echo.
java -cp out ServidorApi ..\frontend 8080
pause
