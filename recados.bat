@echo off
REM Inicia o Recados sem janela de console. Rode "mvn package" antes, se o jar nao existir.
set DIR=%~dp0
if not exist "%DIR%target\recados.jar" (
  echo Jar nao encontrado. Rode: mvn package
  pause
  exit /b 1
)
REM uiScale=1 mantem uma escala so em todos os monitores. Ver "Dois monitores" no README.
start "" javaw -Dsun.java2d.uiScale=1 -jar "%DIR%target\recados.jar"
