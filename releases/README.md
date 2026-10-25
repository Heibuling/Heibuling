# 安装包归档

## 已归档

| 文件 | 版本 | 说明 |
|---|---|---|
| `Heibuling-v0.2.3.apk` | v0.2.3 | 当前最新：删除好友、开发者诊断模式、离线消息补收修复 |

## 未归档（重要说明）

以下版本的安装包**已经不存在了**，因此无法上传：

- `v0.1.0`、`v0.2.0`、`v0.2.1`、`v0.2.2`

原因：每次 `assembleRelease` 都会覆盖同一个输出文件 `app/build/outputs/apk/release/app-release.apk`，
且开发过程中清理过工作区里的历史副本。它们的**变更内容仍完整记录在 [CHANGELOG.md](../CHANGELOG.md) 与
[docs/releases/](../docs/releases/)** 中。

如果想要历史二进制，只有两个办法：
1. 从当时安装过该版本的手机上提取（`adb shell pm path 包名` 找到安装路径后 `adb pull`）；
2. 用当前源码改版本号重新构建 —— 但代码已经不是当时的样子，**不能当作对应版本的原版分发**。

## 以后如何避免

每次发版后立刻归档（`scripts/archive-release.ps1` 已提供）：

```powershell
./scripts/archive-release.ps1 -Version v0.2.4
```

它会先把当前的 release APK 复制成 `releases/Heibuling-v0.2.4.apk`，再让你执行推送脚本。
