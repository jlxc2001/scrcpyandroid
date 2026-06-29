# JLXC Scrcpy Android Client · v0.3 / scrcpy 4.0

这是一个 Android 端 scrcpy 客户端 Demo：安卓设备 A 连接安卓设备 B 的无线 ADB，推送并启动官方 scrcpy-server 4.0，然后在 A 上解码显示 B 的画面并发送基础控制事件。

## v0.3 修复点

- ADB 私钥改存到 `getNoBackupFilesDir()/adbkey.pk8`，并在日志显示 key 指纹，避免同一安装内反复生成新 key。
- 修复私钥存在但公钥 sidecar 丢失时会重新生成 key 的问题，现在会从 RSA 私钥重建公钥。
- scrcpy-server 启动参数改为“短命令模式”，只传必要参数，减少 Samsung/部分厂商机型 `stack corruption detected (-fstack-protector)` 的概率。
- FPS 默认改为 `0`，含义是不传 `max_fps` 给 scrcpy-server。
- 加入“ 三星兼容参数 ”按钮：Max=1024、Bit=4000000、FPS=0。
- 保存 IP、端口、Max、Bit、FPS 输入值。

## 构建

GitHub Actions 会自动下载官方 scrcpy-server v4.0 并放入：

```text
app/src/main/assets/scrcpy-server-v4.0.jar
```

如果你在本地构建，也可以手动下载官方 Release 里的 `scrcpy-server-v4.0`，重命名为 `scrcpy-server-v4.0.jar` 后放到上面的 assets 目录。

## 使用

被控设备开启无线 ADB 后，在客户端里填写被控设备 IP 和端口 5555，然后点“连接 / 启动”。首次连接需要在被控设备上允许 ADB RSA 授权。

如果是 Samsung / One UI 设备，建议先用：

```text
Max = 1024
Bit = 4000000
FPS = 0
```

如果画面稳定后，再逐步提升 Max 和 Bit。FPS=0 代表不限制帧率，而不是 0fps。

## 注意

scrcpy 的 client/server 协议没有前后兼容性，本工程固定按 scrcpy 4.0 处理，server 文件也必须是 4.0。
