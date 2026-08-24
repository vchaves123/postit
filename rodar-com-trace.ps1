# Roda o Recados com o diario de bordo ligado.
#
# Serve para reproduzir um problema: voce usa o programa normalmente e, no fim, o arquivo
# de log conta tecla por tecla o que aconteceu -- e, se a tela congelar, de onde a interface
# nao esta saindo (a pilha de todas as threads, refotografada a cada 3 segundos).
#
# As notas sao as de sempre (~\.recados). E de proposito: o travamento aparece no conteudo
# real, e uma pasta de brinquedo nao o reproduziria.

param(
    # Sem isto o log vai para ~\.recados\trace\ com data e hora no nome.
    [string] $Log,

    # Mostra o log crescendo em tempo real, numa segunda janela.
    [switch] $Acompanhar
)

$ErrorActionPreference = 'Stop'

$dir = $PSScriptRoot
$jar = Join-Path $dir 'target\recados.jar'

if (-not (Test-Path $jar)) {
    Write-Output 'Jar ausente; compilando...'
    & mvn -q -o package
    if ($LASTEXITCODE -ne 0) { Write-Error 'A compilacao falhou.' }
}

# O Recados so aceita uma instancia: se a versao instalada estiver aberta, a de trace sai
# calada e o log fica vazio -- o que parece um bug do trace, e nao e.
$rodando = Get-Process Recados -ErrorAction SilentlyContinue
if ($rodando) {
    Write-Error ('O Recados ja esta em execucao (pid ' + ($rodando.Id -join ', ') +
        '). Feche-o pela bandeja (ou "Sair", Ctrl+Q) antes de rodar com trace.')
}

if (-not $Log) {
    $carimbo = Get-Date -Format 'yyyyMMdd-HHmmss'
    $Log = Join-Path $env:USERPROFILE ".recados\trace\trace-$carimbo.log"
}
New-Item -ItemType Directory -Force (Split-Path $Log) | Out-Null

Write-Output "Diario de bordo: $Log"
Write-Output 'Reproduza o problema e depois feche o Recados (bandeja > Sair, ou Ctrl+Q).'
Write-Output ''

if ($Acompanhar) {
    Start-Process powershell -ArgumentList '-NoExit', '-Command',
        "Get-Content -Wait -Path '$Log'"
}

& java `
    "-Drecados.trace=$Log" `
    '-Dsun.java2d.uiScale=1' `
    -cp $jar com.recados.RecadosApp

Write-Output ''
Write-Output "Pronto. Mande este arquivo: $Log"
