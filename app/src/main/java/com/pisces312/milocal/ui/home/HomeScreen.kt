package com.pisces312.milocal.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pisces312.milocal.R
import com.pisces312.milocal.data.db.DeviceEntity
import com.pisces312.milocal.data.db.GroupEntity
import com.pisces312.milocal.viewmodel.DeviceListViewModel
import com.pisces312.milocal.viewmodel.DeviceTab
import com.pisces312.milocal.viewmodel.DisplayMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddDevice: () -> Unit,
    onDeviceClick: (Long) -> Unit,
    onSettings: () -> Unit,
    vm: DeviceListViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val importResult by vm.importResult.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    if (importResult != null) {
        val r = importResult!!
        AlertDialog(
            onDismissRequest = { vm.clearImportResult() },
            title = { Text("导入结果") },
            text = { Text(if (r.error != null) "导入失败：${r.error}" else "成功导入 ${r.imported} 台设备（识别 ${r.parsed} 台）") },
            confirmButton = { TextButton(onClick = { vm.clearImportResult() }) { Text("确定") } }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false; importText = "" },
            title = { Text("批量导入设备") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("粘贴设备信息") },
                    placeholder = { Text("设备名 (model)\n DID: xxx Token: xxx\n IP: x.x.x.x [状态]") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    maxLines = 20
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.importDevices(importText); showImportDialog = false; importText = "" },
                    enabled = importText.isNotBlank()
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false; importText = "" }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("米控") },
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(painterResource(R.drawable.ic_import), contentDescription = "批量导入")
                    }
                    DisplayModeToggle(state.displayMode, vm::setDisplayMode)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDevice) {
                Icon(Icons.Default.Add, contentDescription = "添加设备")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = { Icon(painterResource(R.drawable.ic_wifi), contentDescription = null) },
                    label = { Text("设备") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onSettings,
                    icon = { Icon(painterResource(R.drawable.ic_settings), contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SubTabRow(state.currentTab, vm::setTab)
            when (state.currentTab) {
                DeviceTab.ALL -> AllDevicesContent(state.devices, state.displayMode, onDeviceClick, vm)
                DeviceTab.GROUP -> GroupDevicesContent(state.devices, state.groups, state.displayMode, onDeviceClick, vm)
                DeviceTab.ROOM -> RoomDevicesContent(state.devices, state.rooms, state.displayMode, onDeviceClick, vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubTabRow(currentTab: DeviceTab, onTabChange: (DeviceTab) -> Unit) {
    val tabs = listOf(DeviceTab.ALL to "全部", DeviceTab.GROUP to "分组", DeviceTab.ROOM to "房间")
    SecondaryTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == currentTab }.coerceAtLeast(0)) {
        tabs.forEach { (tab, label) ->
            Tab(selected = currentTab == tab, onClick = { onTabChange(tab) }, text = { Text(label) })
        }
    }
}

@Composable
private fun DisplayModeToggle(mode: DisplayMode, onChange: (DisplayMode) -> Unit) {
    val next = when (mode) {
        DisplayMode.COMPACT -> DisplayMode.DETAIL
        DisplayMode.DETAIL -> DisplayMode.GRID
        DisplayMode.GRID -> DisplayMode.COMPACT
    }
    val iconRes = when (mode) {
        DisplayMode.COMPACT -> R.drawable.ic_view_list
        DisplayMode.DETAIL -> R.drawable.ic_view_detail
        DisplayMode.GRID -> R.drawable.ic_view_grid
    }
    IconButton(onClick = { onChange(next) }) {
        Icon(painterResource(iconRes), contentDescription = "切换视图")
    }
}

@Composable
private fun AllDevicesContent(
    devices: List<DeviceEntity>,
    displayMode: DisplayMode,
    onDeviceClick: (Long) -> Unit,
    vm: DeviceListViewModel
) {
    if (devices.isEmpty()) {
        EmptyContent("暂无设备", "点击右下角 + 添加设备")
    } else {
        DeviceList(devices, displayMode, onDeviceClick, vm)
    }
}

@Composable
private fun GroupDevicesContent(
    devices: List<DeviceEntity>,
    groups: List<GroupEntity>,
    displayMode: DisplayMode,
    onDeviceClick: (Long) -> Unit,
    vm: DeviceListViewModel
) {
    if (devices.isEmpty()) {
        EmptyContent("暂无设备", "点击右下角 + 添加设备")
        return
    }
    val grouped = devices.groupBy { it.groupId }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { group ->
            val groupDevices = grouped[group.id].orEmpty()
            if (groupDevices.isNotEmpty()) {
                item(key = "header_group_${group.id}") { SectionHeader(group.name, groupDevices.size) }
                items(groupDevices, key = { it.id }) { device ->
                    DeviceItem(device, displayMode, onDeviceClick, vm)
                }
            }
        }
        val ungrouped = grouped[null].orEmpty()
        if (ungrouped.isNotEmpty()) {
            item(key = "header_ungrouped") { SectionHeader("未分组", ungrouped.size) }
            items(ungrouped, key = { it.id }) { device ->
                DeviceItem(device, displayMode, onDeviceClick, vm)
            }
        }
    }
}

@Composable
private fun RoomDevicesContent(
    devices: List<DeviceEntity>,
    rooms: List<String>,
    displayMode: DisplayMode,
    onDeviceClick: (Long) -> Unit,
    vm: DeviceListViewModel
) {
    if (devices.isEmpty()) {
        EmptyContent("暂无设备", "点击右下角 + 添加设备")
        return
    }
    val byRoom = devices.groupBy { it.room }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rooms.forEach { room ->
            val roomDevices = byRoom[room].orEmpty()
            if (roomDevices.isNotEmpty()) {
                item(key = "header_room_$room") { SectionHeader(room, roomDevices.size) }
                items(roomDevices, key = { it.id }) { device ->
                    DeviceItem(device, displayMode, onDeviceClick, vm)
                }
            }
        }
        val unassigned = byRoom[null].orEmpty()
        if (unassigned.isNotEmpty()) {
            item(key = "header_unassigned") { SectionHeader("未分配", unassigned.size) }
            items(unassigned, key = { it.id }) { device ->
                DeviceItem(device, displayMode, onDeviceClick, vm)
            }
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<DeviceEntity>,
    displayMode: DisplayMode,
    onDeviceClick: (Long) -> Unit,
    vm: DeviceListViewModel
) {
    when (displayMode) {
        DisplayMode.GRID -> {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    GridDeviceCard(device, onDeviceClick)
                }
            }
        }
        else -> {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.id }) { device ->
                    DeviceItem(device, displayMode, onDeviceClick, vm)
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(
    device: DeviceEntity,
    displayMode: DisplayMode,
    onDeviceClick: (Long) -> Unit,
    vm: DeviceListViewModel
) {
    when (displayMode) {
        DisplayMode.COMPACT -> CompactDeviceItem(device, onDeviceClick)
        DisplayMode.DETAIL -> DetailDeviceCard(device, onDeviceClick, vm)
        DisplayMode.GRID -> GridDeviceCard(device, onDeviceClick)
    }
}

@Composable
private fun CompactDeviceItem(device: DeviceEntity, onClick: (Long) -> Unit) {
    ListItem(
        headlineContent = { Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(device.ip, maxLines = 1) },
        leadingContent = {
            Icon(painterResource(R.drawable.ic_wifi), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        },
        modifier = Modifier.fillMaxWidth().clickable { onClick(device.id) }
    )
}

@Composable
private fun DetailDeviceCard(device: DeviceEntity, onClick: (Long) -> Unit, vm: DeviceListViewModel) {
    var showDelete by remember { mutableStateOf(false) }
    Card(onClick = { onClick(device.id) }, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(device.name) },
            supportingContent = {
                Text("${device.ip} · ${device.model.ifBlank { device.type }}")
            },
            leadingContent = {
                Icon(painterResource(R.drawable.ic_wifi), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除设备") },
            text = { Text("确定删除「${device.name}」吗？") },
            confirmButton = { TextButton(onClick = { showDelete = false; vm.deleteDevice(device) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun GridDeviceCard(device: DeviceEntity, onClick: (Long) -> Unit) {
    Card(onClick = { onClick(device.id) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_wifi), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(4.dp))
            Text(device.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(device.ip, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        "$title ($count)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun EmptyContent(title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
