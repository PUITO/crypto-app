# Crypto App（Android 本地）

本地运行的事件合约模拟 App：**拉 K 线 → 指标配置信号 → 回测/模拟**，不依赖自建服务器。

## 功能

- 默认拉取 1000 根 K 线（Binance 公共 API）
- 时间段：5m / 10m / 30m / 1h（K 线周期 = 事件时长）
- 策略配置：买入/卖出多条件 **OR**；同时仅启用一套
- **启动策略**时历史回测；**停止**清除模拟数据
- 图表标记 **B / S**；交易历史默认 5 条

## 用 GitHub Actions 构建 APK

可以。仓库已包含工作流 **Build Android APK**（`.github/workflows/android.yml`）：

1. 推送代码到 `master` 或手动 **Actions → Build Android APK → Run**
2. 构建成功后在该次运行的 **Artifacts** 下载 `crypto-app-debug`
3. 手机需允许安装未知来源，安装 debug APK

本地构建：

```bash
# Android Studio 打开本仓库，或：
./gradlew assembleDebug
# 输出：app/build/outputs/apk/debug/
```

## 技术栈

Kotlin · Jetpack Compose · OkHttp · Coroutines  

包名：`com.puito.cryptoapp`
