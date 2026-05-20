# 米控 (MiLocal)

<p align="center">
  <b>局域网小米设备控制工具 — 无需云端登录，无需服务器，纯本地 MIoT UDP 协议直连控制。</b><br>
  <b>Local network Xiaomi device controller — no cloud login, no server, direct MIoT UDP protocol connection.</b>
</p>

## Features / 功能

- 🔌 **LAN Direct / 局域网直连**：通过 MIoT UDP 协议（端口 54321）直接控制设备  
  Control devices directly via MIoT UDP protocol (port 54321)
- 🔒 **Fully Local / 纯本地**：不连接任何云服务，设备 Token 仅存储在本地数据库  
  No cloud services; device tokens stored only in local database
- 💡 **Device Control / 设备控制**：开关、亮度调节（支持灯、插座等常见设备类型）  
  On/off, brightness control (lights, plugs, and more)
- 📱 **Modern UI / 现代界面**：Jetpack Compose + Material 3  
  Built with Jetpack Compose + Material 3
- 📋 **Device Management / 设备管理**：添加/删除设备，本地持久化存储  
  Add/remove devices with local persistent storage

## Screenshots / 截图

> Coming soon / 待补充

## Prerequisites / 前置条件

- Android 8.0 (API 26) or higher / Android 8.0 (API 26) 及以上
- Phone and Xiaomi device on the same LAN / 手机与小米设备在同一局域网
- Device token (see below) / 设备 Token（获取方式见下方）

## Obtaining Device Token / 获取设备 Token

本应用不提供获取 Token 的功能，需通过其他方式获取：  
This app does not provide token extraction; obtain tokens via other means:

1. **小米路由器管理页面 / Xiaomi Router Admin**：部分路由器可以在设备列表中查看 Token  
   Some routers expose tokens in the device list
2. **Mi Home Token Extractor**：[python-miio](https://github.com/AlexxIT/python-miio) 项目提供的工具  
   Tools provided by the python-miio project
3. **从 Mi Home 备份中提取 / Extract from Mi Home backup**：root 设备可从 `/smart_home/devices/local/` 目录获取  
   On rooted devices, extract from `/smart_home/devices/local/`

## Build / 构建

Debug:
```bash
./gradlew assembleDebug
```

Release / Release 构建：
```bash
# Set signing environment variables / 设置签名环境变量
export KEY_STORE_LOCATION=/path/to/keystore
export KEY_ALIAS=your_alias
export KEY_STORE_PASSWORD=your_password
export KEY_PASSWORD=your_password  # optional / 可选，默认同 KEY_STORE_PASSWORD

./gradlew assembleRelease
```

## Tech Stack / 技术栈

| Component / 组件 | Version / 版本 |
|------------------|----------------|
| Kotlin | 2.1.0 |
| Jetpack Compose BOM | 2024.12.01 |
| Material 3 | — |
| Room | 2.6.1 |
| Navigation Compose | 2.8.5 |
| AGP | 8.9.1 |
| Gradle | 9.4.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

## Project Structure / 项目结构

```
app/src/main/java/com/pisces312/milocal/
├── MainActivity.kt              # Entry point / 入口
├── protocol/                    # MIoT protocol stack / MIoT 协议栈
│   ├── MiIoCrypto.kt            # AES-CBC encryption/decryption / AES-CBC 加解密
│   ├── MiIoPacket.kt            # 32-byte header construction & parsing / 32 字节包头构造与解析
│   └── MiIoClient.kt            # UDP communication client / UDP 通信客户端
├── data/
│   ├── db/
│   │   └── DeviceEntity.kt      # Room entity + DAO + Database / Room 实体 + DAO + Database
│   └── repository/
│       └── DeviceRepository.kt  # Data repository / 数据仓库
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

## Protocol / 协议原理

详见 [DESIGN.md](DESIGN.md)  
See [DESIGN.md](DESIGN.md) for protocol details.

## License / 许可证

[Apache-2.0](LICENSE)
