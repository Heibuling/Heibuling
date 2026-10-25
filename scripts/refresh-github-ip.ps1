# 刷新 GitHub 真实 IP 并写入本仓库 git 配置
# 背景：本机 hosts 把 github.com 等 26 个域名指向 127.0.0.1（某 App 写入的屏蔽），
#       且 GitHub 的 github.com IP 在国内会间歇性被墙 —— 所以这里会自动挑一个"连得通"的 IP。
# 何时用：git push / 下载 Release 突然不通时，先跑这个脚本。
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# github.com 候选 IP（实测可用的会排前面；被墙的会自动跳过）
$candidates = @("140.82.112.4","20.27.177.113","20.200.245.247","20.201.28.151","20.205.243.166","140.82.113.4","140.82.114.4","140.82.121.4")
$githubIp = $null
foreach ($ip in $candidates) {
    $code = curl.exe -sS -m 8 --resolve ("github.com:443:" + $ip) -o NUL -w "%{http_code}" "https://github.com" 2>&1
    if ($code -eq "200") { $githubIp = $ip; Write-Host ("  github.com -> " + $ip + " 可用"); break }
    Write-Host ("  github.com -> " + $ip + " 不可用，跳过")
}
if (-not $githubIp) { Write-Host "所有候选 IP 都不通，请稍后重试或使用代理"; exit 1 }

# 其余域名的 IP 用公共 DNS 解析（绕过 hosts）
$others = @{ "api.github.com" = $null; "codeload.github.com" = $null; "objects.githubusercontent.com" = $null; "uploads.github.com" = $null }
git config --unset-all http.curloptResolve 2>$null
git config --add http.curloptResolve ("github.com:443:" + $githubIp)
foreach ($h in $others.Keys) {
    $ip = (Resolve-DnsName $h -Server 223.5.5.5 -Type A -ErrorAction SilentlyContinue |
            Where-Object { $_.IPAddress -and $_.Type -eq "A" } | Select-Object -First 1).IPAddress
    if ($ip) {
        git config --add http.curloptResolve ($h + ":443:" + $ip)
        Write-Host ("  " + $h + " -> " + $ip)
    }
}
git config http.schannelCheckRevoke false
Write-Host "已更新 git 配置，可以直接 git push / git pull 了。"
