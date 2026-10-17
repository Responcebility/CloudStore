# Kasi-Crunch - compile and run the core tests (no database needed).
# Usage:  .\test.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (Test-Path out-test) { Remove-Item -Recurse -Force out-test }
$sources = (Get-ChildItem src/kasicrunch/*.java).FullName + (Get-ChildItem tests/kasicrunch/*.java).FullName
javac -encoding UTF-8 -d out-test -cp "lib/*" $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp "out-test;lib/*" kasicrunch.CoreTest
exit $LASTEXITCODE
