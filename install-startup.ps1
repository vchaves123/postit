# Cria um atalho na pasta Inicializar do Windows para o Recados subir com a sessao.
# Desfazer: uninstall-startup.ps1
#
# Prefere o exe do jpackage (target\dist\Recados\Recados.exe) quando ele existe: a barra de
# tarefas tira o icone do executavel que lancou o processo, e por javaw.exe o botao fica com o
# cafezinho do Java. Sem o exe, cai para javaw + jar e usa o recados.ico no atalho.

$ErrorActionPreference = 'Stop'

$repo = $PSScriptRoot
$exe = Join-Path $repo 'target\dist\Recados\Recados.exe'
$jar = Join-Path $repo 'target\recados.jar'
$ico = Join-Path $repo 'target\recados.ico'

if (Test-Path $exe) {
    $target = $exe
    $arguments = ''
    $workdir = Split-Path $exe -Parent
    $icon = "$exe,0"
} else {
    if (-not (Test-Path $jar)) {
        Write-Error "Nem o exe nem o jar existem. Rode 'mvn package' (e, se quiser o icone proprio na barra de tarefas, .\package-app.ps1)."
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

    $target = $javaw
    $arguments = '-jar "' + $jar + '"'
    $workdir = $repo
    if (-not (Test-Path $ico)) {
        & java -cp $jar com.recados.Icons $ico | Out-Null
    }
    if (Test-Path $ico) { $icon = "$ico,0" } else { $icon = "$javaw,0" }
}

$startup = [Environment]::GetFolderPath('Startup')
$link = Join-Path $startup 'recados.lnk'

# limpa o atalho do nome antigo, para nao sobrar dois na Inicializar
Remove-Item (Join-Path $startup 'postit.lnk') -Force -ErrorAction SilentlyContinue

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($link)
$shortcut.TargetPath = $target
$shortcut.Arguments = $arguments
$shortcut.WorkingDirectory = $workdir
$shortcut.Description = 'Recados - notas na area de trabalho'
$shortcut.IconLocation = $icon
$shortcut.Save()

Write-Output "Atalho criado: $link"
Write-Output "  alvo: $target"
if ($arguments) { Write-Output "  args: $arguments" }
Write-Output "  icone: $icon"
Write-Output 'O Recados vai iniciar no proximo login. Para desfazer: .\uninstall-startup.ps1'
