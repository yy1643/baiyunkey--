# BaiyunKeys Android

一个使用 Android 原生 BLE（低功耗蓝牙）与已授权门禁通信的离线客户端。

本公开版本不包含任何真实门禁 MAC、产品 Key、随机数、握手指令、门锁回包、本机 SDK 路径或签名文件。门禁参数只能由使用者在 App 的“配置”页面手动填写，并使用 Android Keystore 加密保存在当前设备中。

> 仅限连接和测试你本人拥有或已获得明确授权的门禁设备。请勿将本项目用于未经授权的访问。

## 功能

- 原生 Android/Kotlin + Jetpack Compose 实现
- 已知门禁 MAC 的 BLE 直连
- 门锁随机数读取、DES 握手和加密回包校验
- 只有收到门锁的有效确认回包后才显示开门成功
- Android Keystore + AES-GCM 加密保存 MAC 和 Key
- 纯白主页、一键开门、实时调试日志
- 日志自动滚动，支持清空和复制
- 连接、服务发现、读写和回包超时保护
- 桌面快捷方式与小部件

## 开发环境

- Android Studio
- JDK 17
- Android SDK 34 或更高版本
- Gradle 8.4（项目已包含 Wrapper）
- 最低 Android 6.0（API 23）
- 目标 Android 14（API 34），可在更新版本 Android 上运行

## 导入项目

1. 使用 Android Studio 打开本目录。
2. 将 Gradle JDK 设置为 JDK 17。
3. 等待 Gradle Sync 完成。
4. 连接 Android 真机后运行 `app` 配置，或执行：

```bash
./gradlew testDebugUnitTest assembleDebug
```

调试 APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 使用方法

1. 安装 APK 并打开 App。
2. 授予“附近设备/蓝牙”权限。
3. 进入底部“配置”。
4. 输入你自己的门禁 MAC 和 16 位十六进制 Key。
5. 点击“加密保存”。
6. 返回首页，靠近门禁后点击“一键开门”。
7. 在实时日志中查看连接、握手和门锁回包过程。

示例格式（仅为虚构占位数据）：

```text
MAC: AA:BB:CC:DD:EE:FF
Key: 0011223344556677
```

不要把真实参数写入源码、测试、README、Issue、日志截图或 Git 提交。

## 安全设计

- 配置通过 Android Keystore 生成的 AES-GCM 密钥加密。
- 应用数据备份已关闭，避免配置进入云备份或设备迁移包。
- Key 不写入应用源码或构建配置。
- 日志不输出 Key。
- 写入 BLE 特征成功不等于门已打开；应用会继续等待并校验门锁通知。
- Git 忽略规则会排除 APK、数据库、备份、签名文件和本机配置。

卸载 App 会删除本机保存的配置。更换手机后需要重新填写。

## 项目结构

```text
app/src/main/java/cn/huacheng/safebaiyun/
├── compose/     # Compose 页面、配置和实时日志
├── theme/       # 纯白主题
├── unlock/      # 加密配置与 BLE 状态机
├── util/        # 字节处理、DES 和协议逻辑
└── widget/      # 桌面小部件
```

## 测试

公开仓库中的协议测试只使用虚构数据，不包含真实设备信息：

```bash
./gradlew testDebugUnitTest
```

BLE 连接必须在获得授权的真实 Android 设备和门禁环境中验证。

## 来源与发布说明

本项目是在 [dogproton/SafeBaiyun](https://github.com/dogproton/SafeBaiyun) 基础上进行的界面、安全存储、BLE 状态机和日志改造。

公开发布前，请自行确认原项目的授权条款或取得原作者许可。本仓库不应包含真实门禁凭据、签名证书或个人数据。
