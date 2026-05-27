# 冥想 APK Codex

这是从微信小程序 `meditation-timer` 改写出来的原生 Android 工程。

## 当前状态

本文件夹包含完整 Android Studio 工程源码，但当前电脑命令行环境缺少：

- Java/JDK
- Gradle
- Android SDK

所以我无法在当前环境直接生成 `.apk`。安装 Android Studio 后，可以用本工程生成 APK。

## 如何构建 APK

1. 安装 Android Studio。
2. 用 Android Studio 打开本文件夹：`冥想apk-codex`。
3. 等待 Gradle Sync 完成。
4. 点击 `Build > Build Bundle(s) / APK(s) > Build APK(s)`。
5. 生成文件通常在：

   `app/build/outputs/apk/debug/app-debug.apk`

如果你安装的是命令行 Android SDK 和 Gradle，也可以在本文件夹运行：

```powershell
gradle assembleDebug
```

本工程没有附带 Gradle Wrapper，所以当前文件夹里不会有 `gradlew`。

## 功能

- 定时和不定时两种模式
- 1/3/5/10/15/20/30 分钟预设
- 自定义秒数
- 呼吸引导：箱式 4-4-4-4、4-7-8
- 开始颂钵：由强到弱
- 自然结束颂钵：由弱到强
- 手动停止：静默停止，不播放结束颂钵
- 本地历史记录和统计

## 重要文件

- `app/src/main/java/com/ninawang/meditationtimer/MainActivity.java`
- `app/src/main/res/raw/bowl.mp3`
- `app/src/main/res/raw/cue.mp3`
- `docs/design.md`
- `docs/implementation-plan.md`
