# Empacota o Recados como aplicativo nativo do Windows, em target\dist\Recados\Recados.exe.
#
# Por que isso existe: a barra de tarefas do Windows escolhe o icone pelo executavel que
# lancou o processo, nao pelo icone da janela. Rodando por javaw.exe, o botao mostra o
# cafezinho do Java por mais icones que a janela declare -- e Java puro nao tem como definir
# o AppUserModelID que mudaria isso. Com um exe proprio, o icone e o nosso.

$ErrorActionPreference = 'Stop'

$dir = $PSScriptRoot
$jar = Join-Path $dir 'target\recados.jar'
$ico = Join-Path $dir 'target\recados.ico'
$stage = Join-Path $dir 'target\stage'
$dist = Join-Path $dir 'target\dist'

if (-not (Test-Path $jar)) {
    Write-Error "Jar nao encontrado em $jar. Rode 'mvn package' primeiro."
}

# o icone e gerado pelo proprio app, sem ferramenta externa
& java -cp $jar com.recados.Icons $ico
if ($LASTEXITCODE -ne 0) { Write-Error 'Nao foi possivel gerar o icone.' }

# jpackage copia a pasta --input inteira: um diretorio so com o jar evita levar target\ junto
if (Test-Path $stage) { Get-ChildItem $stage -File | Remove-Item -Force }
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item $jar $stage

if (Test-Path (Join-Path $dist 'Recados')) {
    Remove-Item (Join-Path $dist 'Recados') -Recurse -Force
}
New-Item -ItemType Directory -Force $dist | Out-Null

$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) { $jpackage = (Get-Command jpackage.exe).Source }

& $jpackage `
    --type app-image `
    --name Recados `
    --app-version 1.0.0 `
    --vendor 'vchaves123' `
    --description 'Notas adesivas na area de trabalho' `
    --input $stage `
    --main-jar 'recados.jar' `
    --main-class 'com.recados.RecadosApp' `
    --icon $ico `
    --java-options "-Dsun.java2d.uiScale=1" `
    --dest $dist
if ($LASTEXITCODE -ne 0) { Write-Error 'jpackage falhou.' }

$exe = Join-Path $dist 'Recados\Recados.exe'
Write-Output ''
Write-Output "Pronto: $exe"
Write-Output 'Para o inicio automatico apontar para o exe, rode install-startup.ps1 depois disto.'
