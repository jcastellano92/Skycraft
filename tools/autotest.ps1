# PowerShell script to run the local Skycraft Client smoke test / autotest
# Usage: .\tools\autotest.ps1

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
$ModDir = Join-Path $RootDir "mod"

Write-Host "=== Skycraft Local Client Autotest ===" -ForegroundColor Cyan
Write-Host "Target Mod Dir: $ModDir"

Set-Location $ModDir

# Launch Minecraft Forge client with autotest flag enabled
& .\gradlew.bat runClient --console=plain "-Dskycraft.autotest=true"

Write-Host "Autotest run finished." -ForegroundColor Green
$Screenshots = Join-Path $ModDir "build\autotest-screenshots"
if (Test-Path $Screenshots) {
    Write-Host "Screenshots captured at: $Screenshots" -ForegroundColor Cyan
    Get-ChildItem $Screenshots
}

