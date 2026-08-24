# Remove o atalho do Recados da pasta Inicializar do Windows.

$ErrorActionPreference = 'Stop'

$startup = [Environment]::GetFolderPath('Startup')
$link = Join-Path $startup 'recados.lnk'


if (Test-Path $link) {
    Remove-Item $link -Force
    Write-Output "Atalho removido: $link"
} else {
    Write-Output "Nada a remover: $link nao existe."
}
