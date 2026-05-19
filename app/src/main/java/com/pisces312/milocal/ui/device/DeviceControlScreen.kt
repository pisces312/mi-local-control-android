package com.pisces312.milocal.ui.device

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.res.painterResource
import com.pisces312.milocal.R
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pisces312.milocal.data.db.DeviceEntity
import com.pisces312.milocal.viewmodel.DeviceControlViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceControlScreen(
    deviceId: Long,
    onBack: () -> Unit,
    vm: DeviceControlViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val device = state.device

    LaunchedEffect(deviceId) { vm.loadDevice(deviceId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "设备控制") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        if (device == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(device.ip) },
                    supportingContent = { Text(device.model.ifBlank { device.type }) },
                    leadingContent = {
                        Icon(
                            painterResource(if (state.online == true) R.drawable.ic_wifi else R.drawable.ic_wifi_off),
                            contentDescription = null,
                            tint = if (state.online == true) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error
                        )
                    },
                    trailingContent = {
                        if (state.loading) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else Text(
                            when (state.online) {
                                true -> "在线"
                                false -> "离线"
                                null -> "检测中"
                            }
                        )
                    }
                )
            }

            GenericControlPanel(vm, device)

            state.error?.let {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )) {
                    Text(it, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun GenericControlPanel(vm: DeviceControlViewModel, device: DeviceEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("快捷控制", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.setProperty(2, 1, true) }) { Text("开") }
                OutlinedButton(onClick = { vm.setProperty(2, 1, false) }) { Text("关") }
            }

            if (device.type == "light") {
                Text("灯光控制", style = MaterialTheme.typography.titleSmall)
                var brightness by remember { mutableFloatStateOf(50f) }
                Text("亮度: ${brightness.toInt()}%")
                Slider(
                    value = brightness,
                    onValueChange = { brightness = it },
                    onValueChangeFinished = { vm.setProperty(2, 2, brightness.toInt()) },
                    valueRange = 1f..100f
                )
            }
        }
    }
}
