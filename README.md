# JLXC Scrcpy Android Client v0.4

Android-to-Android scrcpy client demo based on scrcpy 4.0.

## v0.4 changes

- Fixed stretched video: default mode is aspect-ratio fit/letterbox.
- Added follow-video orientation: the Android client switches portrait/landscape according to the mirrored stream size.
- Added ADB fallback input: if the scrcpy control socket does not inject touch on some Android/Samsung builds, taps/swipes/BACK/HOME are sent with `adb shell input`.
- Added `ROTATE_DEVICE` control button for the target device.
- Kept the Samsung compatibility defaults from v0.3.

## Notes

The APK build downloads the official scrcpy-server v4.0 asset from Genymobile's GitHub release and packages it as:

`app/src/main/assets/scrcpy-server-v4.0.jar`

scrcpy client and server must use the same version. This project intentionally targets scrcpy 4.0.

If touch becomes double-click-like on devices where scrcpy native control works, turn off `输入:ADB备用开` to switch to pure scrcpy control messages.

## Build

```bash
gradle assembleDebug --stacktrace
```

GitHub Actions workflow is included.
