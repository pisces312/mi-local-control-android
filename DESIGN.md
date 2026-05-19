# 米控 设计文档

## 概述

米控是一款 Android 应用，通过 MIoT UDP 协议在局域网内直接控制小米智能设备，无需云端登录或中间服务器。

## 架构

```
┌─────────────────────────────────────────────┐
│                    UI 层                      │
│  HomeScreen / AddDeviceScreen / DeviceControl │
├─────────────────────────────────────────────┤
│                 ViewModel 层                  │
│  DeviceListVM / AddDeviceVM / DeviceControlVM │
├─────────────────────────────────────────────┤
│                  数据层                       │
│  DeviceRepository → Room (DeviceEntity)       │
├─────────────────────────────────────────────┤
│                 协议层                        │
│  MiIoClient → MiIoPacket → MiIoCrypto        │
└──────────────────┬──────────────────────────┘
                   │ UDP 54321
           ┌───────▼────────┐
           │   小米设备       │
           └────────────────┘
```

## MIoT 协议实现

### 通信方式

- **传输层**：UDP，目标端口 54321
- **加密**：AES-128-CBC
- **编码**：JSON（UTF-8）

### 包格式

32 字节包头 + 加密载荷：

```
偏移   长度   字段
0      2     magic: 0x2131
2      2     length (big-endian, 含包头)
4      4     unknown: 0x00000000
8      4     device_id (big-endian)
12     4     timestamp (Unix, big-endian)
16     16    checksum (MD5)
32     ...   encrypted payload
```

### 加密密钥派生

```
key = MD5(token_hex)                    # 16 字节 AES 密钥
iv  = MD5(key + token_hex_bytes)         # 16 字节初始向量
```

- token 为 32 字符十六进制字符串
- `token_hex_bytes` 是将十六进制字符串逐字节解析

### 校验和计算

```
checksum = MD5(header_bytes[0:16] + token_hex_bytes)
```

包头前 16 字节（不含 checksum 字段本身）拼接 token 的二进制表示，取 MD5。

### 通信流程

1. **发现设备**：发送 `miIO.info` 命令，获取设备型号、固件版本等信息
2. **读取属性**：`get_properties` — 指定 siid/piid 读取设备状态
3. **设置属性**：`set_properties` — 指定 siid/piid/value 控制设备
4. **调用动作**：`action` — 指定 siid/aiid 调用设备方法

### JSON 命令示例

```json
// 发现
{"id":1, "method":"miIO.info", "params":[]}

// 读取开关状态 (siid=2, piid=1)
{"id":2, "method":"get_properties", "params":[{"did":"","siid":2,"piid":1}]}

// 设置开关 (siid=2, piid=1, value=true)
{"id":3, "method":"set_properties", "params":[{"did":"","siid":2,"piid":1,"value":true}]}
```

## 设备类型与属性映射

不同设备类型的 MIoT 属性 ID 不同。当前内置的通用映射：

| 类型 | 属性 | siid | piid | 值类型 |
|------|------|------|------|--------|
| 通用 | 开关 | 2 | 1 | bool |
| 灯 | 亮度 | 2 | 2 | int (1-100) |

> 实际 siid/piid 因设备型号而异，后续版本将支持自定义属性映射。

## 数据存储

- **Room 数据库**：`milocal.db`
- **设备实体字段**：id, name, model, ip, token, type, sortOrder, createdAt
- **加密存储**：Token 以明文存储在 Room 数据库中（应用私有目录，沙箱保护）

### 安全考量

- Token 存储在应用私有数据库，Android 沙箱机制保护
- 不连接任何远程服务器
- 不申请 INTERNET 权限（仅局域网 UDP）
- 无日志上传、无遥测

## 权限

| 权限 | 用途 | 级别 |
|------|------|------|
| android.permission.INTERNET | UDP 局域网通信 | normal |
| android.permission.ACCESS_NETWORK_STATE | 检测网络可用性 | normal |
| android.permission.ACCESS_WIFI_STATE | 获取局域网信息 | normal |

## 依赖

- AndroidX Compose BOM 2024.12.01
- Material 3
- Room 2.6.1 (KSP)
- Navigation Compose 2.8.5
- Lifecycle ViewModel Compose 2.8.7
- DataStore Preferences 1.1.1
- Material Icons Extended

## 未来规划

- [ ] 设备自动发现（mDNS/组播）
- [ ] 自定义属性映射编辑器
- [ ] 设备分组
- [ ] 定时任务
- [ ] 场景联动
- [ ] Token 加密存储（EncryptedSharedPreferences）
- [ ] 深色/浅色主题切换
