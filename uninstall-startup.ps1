# Remove o atalho do postit da pasta Inicializar do Windows.

$ErrorActionPreference = 'Stop'

$link = Join-Path ([Environment]::GetFolderPath('Startup')) 'postit.lnk'

if (Test-Path $link) {
    Remove-Item $link -Force
    Write-Output "Atalho removido: $link"
} else {
    Write-Output "Nada a remover: $link nao existe."
}
