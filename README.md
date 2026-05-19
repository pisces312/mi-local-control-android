# 米控 (MiLocal)

局域网小米设备控制工具 — 无需云端登录，无需服务器，纯本地 MIoT UDP 协议直连控制。

## 功能

- 🔌 局域网直连：通过 MIoT UDP 协议（端口 54321）直接控制设备
- 🔒 纯本地：不连接任何云服务，设备 Token 仅存储在本地数据库
- 💡 设备控制：开关、亮度调节（支持灯、插座等常见设备类型）
- 📱 现代界面：Jetpack Compose + Material 3
- 📋 设备管理：添加/删除设备，本地持久化存储

## 截图

> 待补充

## 前置条件

- Android 8.0 (API 26) 及以上
- 手机与小米设备在同一局域网
- 设备 Token（获取方式见下方）

## 获取设备 Token

本应用不提供获取 Token 的功能，需通过其他方式获取：

1. **小米路由器管理页面**：部分路由器可以在设备列表中查看 Token
2. **Mi Home Token Extractor**：[python-miio](https://github.com/AlexxIT/python-miio) 项目提供的工具
3. **从 Mi Home 备份中提取**：root 设备可从 `/smart_home/devices/local/` 目录获取

## 构建

```bash
./gradlew assembleDebug
```

Release 构建：

```bash
# 设置签名环境变量
export KEY_STORE=/path/to/keystore
export KEY_STORE_PASSWORD=xxx
export KEY_ALIAS=xxx
export KEY_PASSWORD=xxx

./gradlew assembleRelease
```

## 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 2.1.0 |
| Jetpack Compose | BOM 2024.12.01 |
| Material 3 | - |
| Room | 2.6.1 |
| Navigation Compose | 2.8.5 |
| AGP | 8.9.1 |
| Gradle | 9.4.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

## 项目结构

```
app/src/main/java/com/pisces312/milocal/
├── MainActivity.kt              # 入口
├── protocol/                    # MIoT 协议栈
│   ├── MiIoCrypto.kt            # AES-CBC 加解密
│   ├── MiIoPacket.kt            # 32 字节包头构造与解析
│   └── MiIoClient.kt            # UDP 通信客户端
├── data/
│   ├── db/
│   │   └── DeviceEntity.kt      # Room 实体 + DAO + Database
│   └── repository/
│       └── DeviceRepository.kt  # 数据仓库
├── viewmodel/
│   ├── DeviceListViewModel.kt
│   ├── DeviceControlViewModel.kt
│   └── AddDeviceViewModel.kt
└── ui/
    ├── theme/Theme.kt
    ├── navigation/AppNavigation.kt
    ├── home/HomeScreen.kt
    ├── adddevice/AddDeviceScreen.kt
    └── device/DeviceControlScreen.kt
```

## 协议原理

详见 [DESIGN.md](DESIGN.md)

## 许可证

[Apache-2.0](LICENSE)
