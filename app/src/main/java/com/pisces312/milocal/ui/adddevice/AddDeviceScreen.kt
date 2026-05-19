package com.pisces312.milocal.ui.adddevice

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pisces312.milocal.viewmodel.AddDeviceViewModel

private val DEVICE_TYPES = listOf("generic" to "通用", "light" to "灯", "plug" to "插座/空调伴侣", "climate" to "空调")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    vm: AddDeviceViewModel = viewModel()
) {
    val state by vm.state.collectAsState()

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

            // 设备类型选择
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
