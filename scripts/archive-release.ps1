# 归档当前构建出的 release APK 到 releases/ 目录
# 用法（每次发版后立刻执行，避免又被下一次构建覆盖）：
#   ./scripts/archive-release.ps1 -Version v0.2.4
param(
    [Parameter(Mandatory = $true)][string]$Version
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root "app/build/outputs/apk/release/app-release.apk"
if (-not (Test-Path $apk)) {
    throw "找不到 $apk，请先执行 ./gradlew.bat assembleRelease"
}
$dest = Join-Path $root ("releases/Heibuling-" + $Version + ".apk")
Copy-Item $apk $dest -Force
$sizeMb = [math]::Round((Get-Item $dest).Length / 1MB, 2)
Write-Host ("已归档 " + $Version + " -> " + $dest + "（" + $sizeMb + " MB）")
