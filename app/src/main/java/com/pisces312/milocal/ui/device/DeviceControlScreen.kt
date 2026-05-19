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
                    supportingContent = {
                        val parts = mutableListOf<String>()
                        if (device.model.isNotBlank()) parts.add(device.model) else parts.add(device.type)
                        device.room?.let { parts.add(it) }
                        Text(parts.joinToString(" · "))
                    },
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

            GenericControlPanel(vm, device, state)

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
private fun GenericControlPanel(
    vm: DeviceControlViewModel,
    device: DeviceEntity,
    state: com.pisces312.milocal.viewmodel.DeviceControlState
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("快捷控制", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.togglePower(true) },
                    enabled = state.online == true,
                    colors = if (state.power) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                ) { Text("开") }
                Button(
                    onClick = { vm.togglePower(false) },
                    enabled = state.online == true,
                    colors = if (!state.power) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                ) { Text("关") }
            }

            if (device.type == "light") {
                Text("灯光控制", style = MaterialTheme.typography.titleSmall)

                Text("亮度: ${state.brightness}%")
                Slider(
                    value = state.brightness.toFloat(),
                    onValueChange = {},
                    onValueChangeFinished = { /* 由 TextField / 步进控制 */ },
                    valueRange = 1f..100f,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 25, 50, 75, 100).forEach { level ->
                        OutlinedButton(
                            onClick = { vm.setBrightness(level) },
                            enabled = state.online == true,
                            modifier = Modifier.weight(1f)
                        ) { Text("$level%") }
                    }
                }

                Text("色温: ${state.colorTemp}K")
                Slider(
                    value = state.colorTemp.toFloat(),
                    onValueChange = {},
                    onValueChangeFinished = {},
                    valueRange = 2700f..6500f,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2700, 4000, 5000, 6500).forEach { temp ->
                        OutlinedButton(
                            onClick = { vm.setColorTemp(temp) },
                            enabled = state.online == true,
                            modifier = Modifier.weight(1f)
                        ) { Text("${temp}K") }
                    }
                }
            }

            if (device.type == "fan" || device.model.startsWith("zhimi.fan.")) {
                Text("风扇控制", style = MaterialTheme.typography.titleSmall)

                Text("风速: ${state.fanSpeed}%")
                Slider(
                    value = state.fanSpeed.toFloat(),
                    onValueChange = {},
                    onValueChangeFinished = {},
                    valueRange = 1f..100f,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 25, 50, 75, 100).forEach { level ->
                        OutlinedButton(
                            onClick = { vm.setFanSpeed(level) },
                            enabled = state.online == true,
                            modifier = Modifier.weight(1f)
                        ) { Text("$level%") }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.setFanSwing(true) },
                        enabled = state.online == true,
                        colors = if (state.fanSwing) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) { Text("摇头开") }
                    Button(
                        onClick = { vm.setFanSwing(false) },
                        enabled = state.online == true,
                        colors = if (!state.fanSwing) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) { Text("摇头关") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.setFanMode(0) },
                        enabled = state.online == true,
                        colors = if (state.fanMode == 0) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) { Text("直吹") }
                    Button(
                        onClick = { vm.setFanMode(1) },
                        enabled = state.online == true,
                        colors = if (state.fanMode == 1) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
                    ) { Text("自然风") }
                }
            }
        }
    }
}
