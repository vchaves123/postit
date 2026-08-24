# Gera o instalavel do Recados para Windows: um .msi com tudo dentro.
#
# "Tudo dentro" e literal: o jpackage embute um JRE recortado no pacote, entao a maquina que
# instala nao precisa de Java nenhum. Sai tambem o app-image em zip, para quem prefere
# descompactar e rodar sem instalar.
#
# O .msi precisa do WiX Toolset 3 (candle.exe e light.exe) no PATH -- e o que o jpackage usa
# por baixo. Sem WiX, o script ainda produz o zip e avisa o que faltou, em vez de falhar
# inteiro: o portavel e util por si.

param(
    # A versao vai no nome do arquivo e nas propriedades do pacote. O MSI exige
    # maior.menor.correcao, com numeros -- "1.0" ou "v1.0.0" ele rejeita.
    [string] $Version = '1.0.0'
)

$ErrorActionPreference = 'Stop'

$dir = $PSScriptRoot
$jar = Join-Path $dir 'target\recados.jar'
$ico = Join-Path $dir 'target\recados.ico'
$stage = Join-Path $dir 'target\stage'
$dist = Join-Path $dir 'target\dist'
$out = Join-Path $dir 'target\instalador'

if (-not (Test-Path $jar)) {
    Write-Error "Jar nao encontrado em $jar. Rode 'mvn package' primeiro."
}
if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Write-Error "Versao invalida: '$Version'. O MSI exige algo como 1.0.0."
}

# o icone e gerado pelo proprio app, sem ferramenta externa
& java -cp $jar com.recados.Icons $ico
if ($LASTEXITCODE -ne 0) { Write-Error 'Nao foi possivel gerar o icone.' }

# jpackage copia a pasta --input inteira: um diretorio so com o jar evita levar target\ junto
if (Test-Path $stage) { Get-ChildItem $stage -File | Remove-Item -Force }
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item $jar $stage

if (Test-Path (Join-Path $dist 'Recados')) {
    # O Recados rodando a partir de target\dist prende o proprio jar, e o erro que sai daqui
    # ("used by another process") nao diz o que fazer. Este diz.
    if (Get-Process Recados -ErrorAction SilentlyContinue) {
        Write-Error ('O Recados esta em execucao e prende os arquivos de target\dist. ' +
            'Feche-o (Ctrl+Q ou "Sair" no menu da bandeja) e rode de novo.')
    }
    Remove-Item (Join-Path $dist 'Recados') -Recurse -Force
}
New-Item -ItemType Directory -Force $dist | Out-Null
New-Item -ItemType Directory -Force $out | Out-Null

$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) { $jpackage = (Get-Command jpackage.exe).Source }

$comum = @(
    '--name', 'Recados',
    '--app-version', $Version,
    '--vendor', 'vchaves123',
    '--description', 'Recados - notas adesivas na area de trabalho',
    '--input', $stage,
    '--main-jar', 'recados.jar',
    '--main-class', 'com.recados.RecadosApp',
    '--icon', $ico,
    # a mesma opcao que os lancadores passam: uma escala so em todos os monitores.
    # Ver "Dois monitores com escalas diferentes" no README.
    '--java-options', '-Dsun.java2d.uiScale=1'
)

# ---------------------------------------------------------------- portavel (zip)

Write-Output '== app-image (portavel)'
& $jpackage @comum --type app-image --dest $dist
if ($LASTEXITCODE -ne 0) { Write-Error 'jpackage falhou ao gerar o app-image.' }

$zip = Join-Path $out "Recados-$Version-windows-portavel.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path (Join-Path $dist 'Recados') -DestinationPath $zip
Write-Output "   $zip"

# ---------------------------------------------------------------- instalador (msi)

$wix = Get-Command candle.exe -ErrorAction SilentlyContinue
if (-not $wix) {
    Write-Warning 'WiX Toolset 3 nao encontrado no PATH (candle.exe); o .msi nao foi gerado.'
    Write-Warning 'Instale com: choco install wixtoolset --version 3.11.2 -y'
    Write-Output ''
    Write-Output "Pronto (so o portavel): $zip"
    exit 0
}

Write-Output '== msi (instalador)'
& $jpackage @comum `
    --type msi `
    --dest $out `
    --win-menu `
    --win-menu-group 'Recados' `
    --win-shortcut `
    --win-dir-chooser `
    --win-upgrade-uuid '7d0f6a52-1c8e-4a3b-9f4d-2b6c5e8a9d31' `
    --win-per-user-install
if ($LASTEXITCODE -ne 0) { Write-Error 'jpackage falhou ao gerar o msi.' }

# o jpackage nomeia "Recados-1.0.0.msi"; o sufixo diz de que plataforma e o pacote
$msi = Get-ChildItem $out -Filter '*.msi' | Sort-Object LastWriteTime | Select-Object -Last 1
$alvo = Join-Path $out "Recados-$Version-windows.msi"
if ($msi.FullName -ne $alvo) { Move-Item $msi.FullName $alvo -Force }

Write-Output ''
Write-Output "Instalador: $alvo"
Write-Output "Portavel:   $zip"
