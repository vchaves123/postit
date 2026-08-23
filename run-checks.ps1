# Compila e roda as checagens contra o jar. Rode "mvn package" antes.
# Sem JUnit/surefire de proposito: veja o comentario em checks/Check.java.

$ErrorActionPreference = 'Stop'

$dir = $PSScriptRoot
$jar = Join-Path $dir 'target\recados.jar'
$out = Join-Path $dir 'target\checks'

if (-not (Test-Path $jar)) {
    Write-Error "Jar nao encontrado em $jar. Rode 'mvn package' primeiro."
}

if (-not (Test-Path $out)) { New-Item -ItemType Directory -Force $out | Out-Null }

$fontes = Get-ChildItem (Join-Path $dir 'checks') -Filter *.java | ForEach-Object { $_.FullName }
& javac -cp $jar -d $out $fontes
if ($LASTEXITCODE -ne 0) { Write-Error 'As checagens nao compilaram.' }

& java -cp "$jar;$out" Checks
exit $LASTEXITCODE
