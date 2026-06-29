# scrcpy-server 获取方式

本工程固定使用 scrcpy v4.0。

## 推荐方式：不用手动放文件

v0.2 开始，`app/build.gradle` 会在构建前自动从官方 GitHub Release 下载：

```text
https://github.com/Genymobile/scrcpy/releases/download/v4.0/scrcpy-server-v4.0
```

然后保存为：

```text
app/src/main/assets/scrcpy-server-v4.0.jar
```

所以你直接上传 GitHub Actions 构建即可。

## 手动方式

如果你本地无网络，或者自动下载失败：

1. 打开官方仓库 Release：Genymobile/scrcpy v4.0。
2. 下载 asset：`scrcpy-server-v4.0`。
3. 重命名为：`scrcpy-server-v4.0.jar`。
4. 放到：

```text
app/src/main/assets/scrcpy-server-v4.0.jar
```

注意：scrcpy 客户端和 server 版本必须完全一致。本工程写死 `4.0`。
