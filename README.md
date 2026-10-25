# Heibuling

> 仓库：https://github.com/Heibuling/Heibuling ・ 发布页：https://github.com/Heibuling/Heibuling/releases

几个人之间用的"弹幕式"消息 App：你输入文字 / 表情 / 图片 / 语音 / 涂鸦，对方手机**屏幕顶部会弹出一条半透明的玻璃气泡**，
约 5 秒后自动消失。反向亦然。不是聊天软件，而是一个"轻打扰的即时表情传递器"。

- **Android**：10 及以上（minSdk 29 / targetSdk 36）
- **不依赖** Firebase / FCM / 账号体系
- 消息通道复用公共 **ntfy.sh**（协议公开，可随时换成自建实例）
- 仅侧载分发，不上架应用商店

> ⚠️ 消息内容**没有端到端加密**，会经过 ntfy.sh 中转；每条"你 ↔ 好友"的通道名是一串 26 位随机字符，等同于密码。
> ⚠️ 图片 / 语音 / 涂鸦作为附件在服务端**只保留 3 小时**（ntfy.sh 限制），超时后需要重发。

## 下载

| 版本 | 说明 |
|---|---|
| [v0.2.3](releases/Heibuling-v0.2.3.apk) | 最新版：删除好友 + 开发者诊断模式 + 离线消息补收修复 |

历史版本（v0.1.0 / v0.2.0 / v0.2.1 / v0.2.2）的安装包**未归档**，详见 [releases/README.md](releases/README.md)。

## 文档

- [CHANGELOG.md](CHANGELOG.md) — 全部版本更新日志
- [朋友端安装说明.md](朋友端安装说明.md) — 给好友看的一页说明（安装 / 权限 / 配对 / 各品牌保活 / 排查）
- [提示词.md](提示词.md) — 完整开发提示词 + 实测记录与踩坑清单（16 章）

## 开发者模式

主页最下方「开发者模式」→ 密码进入。面板可查看每个 topic 的订阅状态、最后事件与服务端消息 id、
最后错误、发送失败的消息与最近事件日志，并提供「强制重连（补收漏掉的消息）」与「复制完整诊断信息」。

## 技术要点

| 关注点 | 位置 |
|---|---|
| 自定义信封协议 | `app/src/main/java/com/example/ntfypop/domain/Envelope.kt` |
| ntfy 发布 / 附件上传 / 下载 | `.../data/remote/NtfyClient.kt` |
| ntfy 长连接订阅 | `.../data/remote/NtfyStream.kt` |
| 前台服务（specialUse，避开 Android 15 六小时限制） | `.../service/LinkService.kt` |
| 悬浮窗与气泡 | `.../service/overlay/` |
| 通知与常驻文案池 | `.../service/NotificationFactory.kt` |
| 本地存储（轻量 JSON，原子写入） | `.../data/local/Store.kt` |

## 本机网络说明（重要）

这台机器的 hosts 文件里有一段被某 App 写入的 GitHub 屏蔽规则（26 个域名指向 127.0.0.1），会让 git push 与 Release 上传全部失败。

1. 临时绕过（已配置，无需管理员）：本仓库 .git/config 里已写入 http.curloptResolve，git 直连真实 IP；失效时执行 ./scripts/refresh-github-ip.ps1
2. 彻底修复（需管理员）：管理员 PowerShell 删掉 hosts 里所有含 github 的行，再 ipconfig /flushdns

## 构建

项目自带便携工具链时无需 Android Studio：

```powershell
$env:JAVA_HOME = "$PWD/tools/jdk17"
$env:ANDROID_HOME = "$PWD/tools/android-sdk"
./gradlew.bat assembleRelease     # 产物：app/build/outputs/apk/release/app-release.apk
```

> 签名密钥 `ntfypop.jks` 与 `keystore.properties` **不在仓库里**（已在 .gitignore 中排除），请单独备份。
> 覆盖升级必须使用同一个密钥。
