# JLXC Scrcpy Android Client

一个基于 **scrcpy 4.0 协议思路**写的 Android 端客户端 Demo：

- 安卓设备 A 运行本 App；
- 安卓设备 B 开启无线 ADB `5555`；
- 本 App 直接连接 B 的 ADB daemon；
- 推送并启动 `scrcpy-server-v4.0.jar`；
- 通过 ADB `localabstract:scrcpy_<scid>` 打开 video/control socket；
- 用 Android `MediaCodec` 解码 H.264；
- 用 scrcpy v4.0 控制协议发送触摸、BACK、HOME。

> 这是工程骨架 / Demo 版，不是 Genymobile 官方客户端。scrcpy 协议是内部协议，未来版本可能变化。

## 重要限制

1. **必须使用与客户端匹配的 scrcpy-server**
   
   这个工程固定按 scrcpy `4.0` 写。请把官方 release 里的 `scrcpy-server` 放到：

   ```text
   app/src/main/assets/scrcpy-server-v4.0.jar
   ```

   文件名必须一致。不要从第三方网站下载。

2. **目标机必须开启无线 ADB**

   先用电脑执行一次：

   ```bash
   adb tcpip 5555
   adb shell ip addr show wlan0
   ```

   然后在本 App 里填目标机 IP，例如 `192.168.1.100`。

3. **首次连接要在目标机上点 RSA 授权**

   本 App 会生成自己的 ADB key。第一次连接时，目标机上会弹出“允许 USB 调试吗？”之类的授权窗口。

4. **当前 Demo 只做 H.264 视频 + 基础触控**

   音频、剪贴板、多指、UHID 键鼠、H.265/AV1、虚拟显示等可以继续加。

## 推荐参数

在车机 / 平板互投场景，建议先用：

```text
max_size = 1280
video_bit_rate = 8000000
max_fps = 60
video_codec = h264
```

如果延迟高，先降：

```text
max_size = 1024
video_bit_rate = 4000000
max_fps = 60
```

## 构建

Android Studio 打开本目录即可，或者用 GitHub Actions 自动构建。

本工程自带：

```text
.github/workflows/android.yml
```

推送到 GitHub 后，在 Actions 里运行 `assembleDebug`，产物在 `app/build/outputs/apk/debug/`。当前工程没有内置 Gradle Wrapper，工作流会用 `gradle/actions/setup-gradle` 指定 Gradle 8.10.2。

## 项目结构

```text
app/src/main/java/com/jlxc/scrcpyclient/
  MainActivity.java                UI、SurfaceView、触控映射
  adb/
    AdbConnection.java             最小 ADB 连接/认证/多路 stream
    AdbKey.java                    ADB RSA key 生成和签名
    AdbPacket.java                 ADB 包格式
    AdbStream.java                 ADB logical stream -> InputStream/OutputStream
    SyncService.java               ADB sync push
  scrcpy/
    ScrcpySession.java             推送 server、启动 app_process、打开 socket
    ScrcpyVideoDemuxer.java        scrcpy v4.0 视频包解析
    H264Decoder.java               Android MediaCodec H.264 解码
    ScrcpyControl.java             scrcpy v4.0 控制消息：触摸/BACK/HOME
    Binary.java                    大端读写工具
```

## 继续开发方向

- 多指触控：按 scrcpy `INJECT_TOUCH_EVENT` 使用不同 pointer id；
- 音频：打开 audio socket，解析 OPUS/AAC/RAW，再用 AudioTrack 播放；
- 熄屏投屏：启动参数加 `turn_screen_off=true`，同时处理唤醒逻辑；
- H.265：把 `video_codec=h265`，decoder MIME 改成 `video/hevc`；
- 低延迟优化：MediaCodec 异步回调、Surface 尺寸固定、解码线程优先级提升；
- 车机版：隐藏顶部控制面板，长按屏幕进入设置，保存目标 IP 和画质参数。
