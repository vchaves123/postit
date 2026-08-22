@echo off
REM Inicia o postit sem janela de console. Rode "mvn package" antes, se o jar nao existir.
set DIR=%~dp0
if not exist "%DIR%target\postit.jar" (
  echo Jar nao encontrado. Rode: mvn package
  pause
  exit /b 1
)
start "" javaw -jar "%DIR%target\postit.jar"
