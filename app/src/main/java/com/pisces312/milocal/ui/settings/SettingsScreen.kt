package com.pisces312.milocal.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pisces312.milocal.R
import com.pisces312.milocal.data.db.GroupEntity
import com.pisces312.milocal.viewmodel.DeviceListViewModel
import com.pisces312.milocal.viewmodel.GroupViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    deviceVm: DeviceListViewModel = viewModel(),
    groupVm: GroupViewModel = viewModel()
) {
    val context = LocalContext.current
    val groupState by groupVm.state.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportDevices(context, deviceVm, it) }
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
                    onClick = { deviceVm.importDevices(importText); showImportDialog = false; importText = "" },
                    enabled = importText.isNotBlank()
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false; importText = "" }) { Text("取消") }
            }
        )
    }

    if (groupState.showAddDialog || groupState.showEditDialog) {
        GroupDialog(
            group = groupState.editing,
            onConfirm = { name, color ->
                if (groupState.showAddDialog) groupVm.addGroup(name, color)
                else groupState.editing?.let { groupVm.updateGroup(it, name, color) }
            },
            onDismiss = groupVm::dismissDialog
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader("数据管理") }
            item {
                ListItem(
                    headlineContent = { Text("批量导入设备") },
                    leadingContent = { Icon(painterResource(R.drawable.ic_import), contentDescription = null) },
                    modifier = Modifier.clickable { showImportDialog = true }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("导出设备 JSON") },
                    leadingContent = { Icon(painterResource(R.drawable.ic_export), contentDescription = null) },
                    modifier = Modifier.clickable { exportLauncher.launch("milocal-devices.json") }
                )
            }
            item { SectionHeader("分组管理") }
            items(groupState.groups, key = { it.id }) { group ->
                ListItem(
                    headlineContent = { Text(group.name) },
                    leadingContent = {
                        Surface(shape = CircleShape, color = parseColor(group.color), modifier = Modifier.size(24.dp)) {}
                    },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { groupVm.showEditDialog(group) }) { Text("编辑") }
                            TextButton(onClick = { groupVm.deleteGroup(group) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                )
            }
            item {
                OutlinedButton(
                    onClick = { groupVm.showAddDialog() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("添加分组") }
            }
            item { SectionHeader("关于") }
            item {
                ListItem(
                    headlineContent = { Text("米控 v1.0.0") },
                    supportingContent = { Text("MIoT 本地控制 · Apache 2.0") }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun GroupDialog(
    group: GroupEntity?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(group?.name ?: "") }
    var color by remember { mutableStateOf(group?.color ?: "#1976D2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (group == null) "添加分组" else "编辑分组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("分组名称") }, singleLine = true)
                OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("颜色 (hex)") }, singleLine = true, placeholder = { Text("#1976D2") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("#1976D2", "#388E3C", "#F57C00", "#D32F2F", "#7B1FA2", "#00796B").forEach { c ->
                        Surface(
                            shape = CircleShape,
                            color = parseColor(c),
                            modifier = Modifier.size(32.dp).clickable { color = c },
                            border = if (color.equals(c, ignoreCase = true)) ButtonDefaults.outlinedButtonBorder else null
                        ) {}
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onConfirm(name, color) }, enabled = name.isNotBlank()) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun parseColor(hex: String): Color {
    return try {
        val c = hex.removePrefix("#")
        Color(("FF$c").toLong(16))
    } catch (_: Exception) {
        Color.Gray
    }
}

private fun exportDevices(context: Context, vm: DeviceListViewModel, uri: android.net.Uri) {
    val devices = vm.state.value.devices
    val json = JSONObject().apply {
        put("version", 1)
        val arr = JSONArray()
        devices.forEach { d ->
            arr.put(JSONObject().apply {
                put("name", d.name)
                put("model", d.model)
                put("did", 0)
                put("token", d.token)
                put("ip", d.ip)
                put("type", d.type)
                d.room?.let { put("room", it) }
                d.groupId?.let { put("groupId", it) }
            })
        }
        put("devices", arr)
    }
    try {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            OutputStreamWriter(os).use { it.write(json.toString(2)) }
        }
        Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
