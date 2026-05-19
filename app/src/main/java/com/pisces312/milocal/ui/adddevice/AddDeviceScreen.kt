package com.pisces312.milocal.ui.adddevice

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pisces312.milocal.R
import com.pisces312.milocal.data.db.AppDatabase
import com.pisces312.milocal.viewmodel.AddDeviceViewModel
import com.pisces312.milocal.viewmodel.GroupViewModel

private val DEVICE_TYPES = listOf("generic" to "通用", "light" to "灯", "plug" to "插座/空调伴侣", "climate" to "空调")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    vm: AddDeviceViewModel = viewModel(),
    groupVm: GroupViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val groupState by groupVm.state.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加设备") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::updateName,
                label = { Text("设备名称 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.ip,
                onValueChange = vm::updateIp,
                label = { Text("IP 地址 *") },
                placeholder = { Text("192.168.1.xxx") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.token,
                onValueChange = vm::updateToken,
                label = { Text("Token *") },
                placeholder = { Text("32位十六进制") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.model,
                onValueChange = vm::updateModel,
                label = { Text("型号（可选）") },
                placeholder = { Text("yeelink.light.lamp1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Device type
            var typeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                OutlinedTextField(
                    value = DEVICE_TYPES.find { it.first == state.type }?.second ?: "通用",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("设备类型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    DEVICE_TYPES.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { vm.updateType(key); typeExpanded = false }
                        )
                    }
                }
            }

            // Group
            var groupExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = groupExpanded, onExpandedChange = { groupExpanded = it }) {
                OutlinedTextField(
                    value = groupState.groups.find { it.id == state.groupId }?.name ?: "无",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("分组") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = groupExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = groupExpanded, onDismissRequest = { groupExpanded = false }) {
                    DropdownMenuItem(text = { Text("无") }, onClick = { vm.updateGroupId(null); groupExpanded = false })
                    groupState.groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(group.name) },
                            onClick = { vm.updateGroupId(group.id); groupExpanded = false }
                        )
                    }
                }
            }

            // Room
            var roomExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = roomExpanded, onExpandedChange = { roomExpanded = it }) {
                OutlinedTextField(
                    value = state.room,
                    onValueChange = { vm.updateRoom(it); roomExpanded = false },
                    label = { Text("房间（可选）") },
                    placeholder = { Text("客厅、卧室…") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roomExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                if (state.existingRooms.isNotEmpty()) {
                    ExposedDropdownMenu(expanded = roomExpanded, onDismissRequest = { roomExpanded = false }) {
                        state.existingRooms.forEach { room ->
                            DropdownMenuItem(text = { Text(room) }, onClick = { vm.updateRoom(room); roomExpanded = false })
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = vm::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.saving) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text("保存")
            }
        }
    }
}
