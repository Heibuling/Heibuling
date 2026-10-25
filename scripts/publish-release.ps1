# 把仓库与标签推到 GitHub，并创建 Release + 上传 APK
# 【必须在能访问 github.com 的网络下运行】
# 用法：
#   ./scripts/publish-release.ps1 -Repo "你的用户名/仓库名" -Token "ghp_xxx" -Version v0.2.3
param(
    [Parameter(Mandatory = $true)][string]$Repo,
    [Parameter(Mandatory = $true)][string]$Token,
    [Parameter(Mandatory = $true)][string]$Version
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "== 1/2 推送源码与标签 =="
$url = "https://" + $Token + "@github.com/" + $Repo + ".git"
git remote remove origin 2>$null
git remote add origin $url
git push origin HEAD --tags

Write-Host "== 2/2 创建 Release 并上传 APK =="
try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 } catch { }
$headers = @{ Authorization = "token " + $Token; Accept = "application/vnd.github+json"; "User-Agent" = "Heibuling-release" }
$notesPath = "docs/releases/" + $Version + ".md"
$notes = if (Test-Path $notesPath) { Get-Content $notesPath -Raw } else { "Heibuling " + $Version }
$payload = @{ tag_name = $Version; name = "Heibuling " + $Version; body = $notes; draft = $false; prerelease = $false } | ConvertTo-Json
$rel = Invoke-RestMethod -Method Post -Uri ("https://api.github.com/repos/" + $Repo + "/releases") -Headers $headers -Body $payload -ContentType "application/json"

$apk = "releases/Heibuling-" + $Version + ".apk"
if (Test-Path $apk) {
    $assetName = Split-Path $apk -Leaf
    $uploadUrl = $rel.upload_url -replace "\{.*", ("?name=" + $assetName)
    Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $headers -ContentType "application/vnd.android.package-archive" -InFile $apk | Out-Null
    Write-Host ("已上传 " + $assetName)
} else {
    Write-Host ("未找到 " + $apk + "，跳过 APK 上传（可先跑 scripts/archive-release.ps1）")
}
Write-Host ("完成：https://github.com/" + $Repo + "/releases/tag/" + $Version)
