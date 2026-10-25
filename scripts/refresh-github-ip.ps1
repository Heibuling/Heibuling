# 刷新 GitHub 真实 IP 并写入本仓库 git 配置
# 用途：本机 hosts 把 github.com 等 26 个域名指向了 127.0.0.1（某 App 写入的屏蔽），
#       用 curloptResolve 直接指定真实 IP 即可绕过，无需管理员权限。
# 何时用：git push / 下载 Release 突然不通时，先跑一次这个脚本。
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$targets = @("github.com","api.github.com","codeload.github.com","objects.githubusercontent.com","uploads.github.com")
git config --unset-all http.curloptResolve 2>$null
foreach ($h in $targets) {
    $ip = (Resolve-DnsName $h -Server 223.5.5.5 -Type A -ErrorAction SilentlyContinue |
            Where-Object { $_.IPAddress -and $_.Type -eq "A" } |
            Select-Object -First 1).IPAddress
    if ($ip) {
        git config --add http.curloptResolve ($h + ":443:" + $ip)
        Write-Host ("  " + $h + " -> " + $ip)
    } else {
        Write-Host ("  " + $h + " 解析失败，跳过")
    }
}
git config http.schannelCheckRevoke false
Write-Host "已更新 git 配置（http.curloptResolve）"
Write-Host ""
Write-Host "彻底修复（需要管理员权限，一次性）：在管理员 PowerShell 里执行："
Write-Host '  $hosts = "$env:windir\System32\drivers\etc\hosts"'
Write-Host '  Copy-Item $hosts "$hosts.bak"'
Write-Host '  (Get-Content $hosts) | Where-Object { $_ -notmatch "github" } | Set-Content $hosts -Encoding ASCII'
Write-Host '  ipconfig /flushdns'
