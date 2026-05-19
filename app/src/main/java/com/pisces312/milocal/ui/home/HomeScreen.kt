package com.pisces312.milocal.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.res.painterResource
import com.pisces312.milocal.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pisces312.milocal.data.db.DeviceEntity
import com.pisces312.milocal.viewmodel.DeviceListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddDevice: () -> Unit,
    onDeviceClick: (Long) -> Unit,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    maxLines = 20
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.importDevices(importText)
                        showImportDialog = false
                        importText = ""
                    },
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
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.devices.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无设备", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("点击右下角 + 添加设备", style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceClick(device.id) },
                        onDelete = { vm.deleteDevice(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        ListItem(
            headlineContent = { Text(device.name) },
            supportingContent = {
                Text("${device.ip} · ${device.model.ifBlank { device.type }}")
            },
            leadingContent = {
                Icon(
                    painterResource(R.drawable.ic_wifi),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
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
            confirmButton = {
                TextButton(onClick = { showDelete = false; onDelete() }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}
