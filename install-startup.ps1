# Cria um atalho na pasta Inicializar do Windows para o postit subir com a sessao.
# Aponta direto para javaw.exe (sem janela de console) em vez de para o .bat.
# Desfazer: uninstall-startup.ps1

$ErrorActionPreference = 'Stop'

$repo = $PSScriptRoot
$jar = Join-Path $repo 'target\postit.jar'

if (-not (Test-Path $jar)) {
    Write-Error "Jar nao encontrado em $jar. Rode 'mvn package' primeiro."
}

# javaw.exe: JAVA_HOME tem prioridade, depois o PATH.
$javaw = $null
if ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME 'bin\javaw.exe'
    if (Test-Path $candidate) { $javaw = $candidate }
}
if (-not $javaw) {
    $onPath = Get-Command javaw.exe -ErrorAction SilentlyContinue
    if ($onPath) { $javaw = $onPath.Source }
}
if (-not $javaw) {
    Write-Error 'javaw.exe nao encontrado. Defina JAVA_HOME ou coloque o JDK 21 no PATH.'
}

$startup = [Environment]::GetFolderPath('Startup')
$link = Join-Path $startup 'postit.lnk'

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($link)
$shortcut.TargetPath = $javaw
$shortcut.Arguments = '-jar "' + $jar + '"'
$shortcut.WorkingDirectory = $repo
$shortcut.Description = 'postit - notas na area de trabalho'
$shortcut.IconLocation = $javaw + ',0'
$shortcut.Save()

Write-Output "Atalho criado: $link"
Write-Output "  alvo: $javaw"
Write-Output "  args: $($shortcut.Arguments)"
Write-Output 'O postit vai iniciar no proximo login. Para desfazer: .\uninstall-startup.ps1'
