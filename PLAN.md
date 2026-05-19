# 米控 功能扩展计划

## v2.0.0 新功能

### 1. 数据模型变更

#### DeviceEntity 新增字段
| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `groupId` | Long? | null | 所属分组 |
| `room` | String? | null | 房间名 |

数据库版本 1→2，Room Migration。

#### 新增 GroupEntity
| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long (PK, autoGenerate) | 自增 |
| `name` | String | 分组名（唯一） |
| `order` | Int | 排序权重 |
| `color` | String | 颜色(hex, 如 "#FF5722") |

关系：Device N:1 Group（一个设备只属于一个分组）

### 2. 界面重构

#### 底部导航（BottomNavigation）
| Tab | 图标 | 内容 |
|-----|------|------|
| 设备 | wifi | 设备管理（含子Tab） |
| 设置 | settings | 导入/导出/分组管理/关于 |

#### 设备 Tab 内三个子Tab（仿StreamClip顶部Tab）
| 子Tab | 内容 | 组织方式 |
|--------|------|---------|
| 全部 | 所有设备平铺 | 当前 DeviceCard 列表 |
| 分组 | 按分组折叠 | 分组名 header + 设备列表，未分组归入"未分组" |
| 房间 | 按房间折叠 | 房间名 header + 设备列表，无房间归入"未分配" |

每个子Tab内保留三种显示模式（TopBar 右侧切换）：
- 精简列表：单行，名称+IP
- 详情列表：当前样式
- 网格卡片：2列平铺

#### 设置 Tab
- 批量导入设备（现有功能移入）
- 导出设备 JSON（SAF 选目录）
- 分组管理（增删改排序+选颜色）
- 关于

### 3. 导入导出

#### 导出 JSON 格式
```json
{
  "version": 1,
  "devices": [
    {
      "name": "客厅灯",
      "model": "yeelink.light.lamp22",
      "did": 123456,
      "token": "abcdef...",
      "ip": "192.168.1.100",
      "type": "light",
      "room": "客厅",
      "group": "灯光"
    }
  ]
}
```

### 4. 新增/修改文件清单

#### 新增
- `data/db/GroupEntity.kt` — 分组实体 + GroupDao
- `data/repository/GroupRepository.kt` — 分组仓库
- `viewmodel/GroupViewModel.kt` — 分组管理 VM
- `ui/home/AllDevicesTab.kt` — 全部设备子Tab
- `ui/home/GroupTab.kt` — 分组子Tab
- `ui/home/RoomTab.kt` — 房间子Tab
- `ui/settings/SettingsScreen.kt` — 设置页
- `ui/settings/GroupManageScreen.kt` — 分组管理页
- `ui/navigation/MainBottomNav.kt` — 底部导航

#### 修改
- `data/db/DeviceEntity.kt` — 新增 groupId/room，DB version 2，Migration
- `data/repository/DeviceRepository.kt` — 新增按分组/房间查询
- `viewmodel/DeviceListViewModel.kt` — 支持分组/房间/显示模式
- `ui/home/HomeScreen.kt` — 重构为 Tab 布局
- `ui/adddevice/AddDeviceScreen.kt` — 新增分组选择、房间输入
- `ui/device/DeviceControlScreen.kt` — 显示分组/房间信息
- `ui/navigation/AppNavigation.kt` — 新增设置路由、底部导航
- `MainActivity.kt` — 适配新导航

### 5. 实施顺序

1. 数据模型：GroupEntity + DeviceEntity 迁移
2. 底部导航框架
3. 设备 Tab 三个子Tab（全部/分组/房间）
4. 显示模式切换（精简/详情/网格）
5. 设置页（导入移入 + 导出 JSON）
6. 分组管理页
7. AddDeviceScreen 增强（分组选择+房间输入）
8. 测试 + 构建
