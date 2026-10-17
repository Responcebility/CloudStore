# CloudStore - build and run on Windows (PowerShell).
# Usage:  .\run.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$driverVersion = "8.4.0"
$driver = "lib/mysql-connector-j-$driverVersion.jar"
if (-not (Test-Path $driver)) {
    Write-Host "Downloading the MySQL JDBC driver $driverVersion..."
    New-Item -ItemType Directory -Force lib | Out-Null
    Invoke-WebRequest "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/$driverVersion/mysql-connector-j-$driverVersion.jar" -OutFile $driver
}

if (-not (Test-Path "config.properties")) {
    Copy-Item "config.example.properties" "config.properties"
    Write-Host "Created config.properties - add your MySQL password to it, then run this script again."
    exit 1
}

Write-Host "Compiling..."
if (Test-Path out) { Remove-Item -Recurse -Force out }
javac -encoding UTF-8 -d out -cp "lib/*" (Get-ChildItem src/cloudstore/*.java).FullName
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Starting CloudStore..."
java -cp "out;lib/*" cloudstore.Main
