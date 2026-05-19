package com.pisces312.milocal.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pisces312.milocal.data.db.AppDatabase
import com.pisces312.milocal.data.db.DeviceEntity
import com.pisces312.milocal.data.db.GroupEntity
import com.pisces312.milocal.data.repository.DeviceRepository
import com.pisces312.milocal.data.repository.GroupRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class DisplayMode { COMPACT, DETAIL, GRID }
enum class DeviceTab { ALL, GROUP, ROOM }

data class DeviceUiState(
    val devices: List<DeviceEntity> = emptyList(),
    val groups: List<GroupEntity> = emptyList(),
    val rooms: List<String> = emptyList(),
    val currentTab: DeviceTab = DeviceTab.ALL,
    val displayMode: DisplayMode = DisplayMode.DETAIL,
    val loading: Boolean = false
)

class DeviceListViewModel(app: Application) : AndroidViewModel(app) {
    private val deviceRepo = DeviceRepository(AppDatabase.getInstance(app).deviceDao())
    private val groupRepo = GroupRepository(AppDatabase.getInstance(app).groupDao())

    private val _state = MutableStateFlow(DeviceUiState())
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    init {
        viewModelScope.launch {
            combine(deviceRepo.allDevices(), groupRepo.allGroups(), deviceRepo.getRooms()) { devices, groups, rooms ->
                _state.update { it.copy(devices = devices, groups = groups, rooms = rooms, loading = false) }
            }.collect()
        }
    }

    fun setTab(tab: DeviceTab) = _state.update { it.copy(currentTab = tab) }
    fun setDisplayMode(mode: DisplayMode) = _state.update { it.copy(displayMode = mode) }

    fun deleteDevice(device: DeviceEntity) {
        viewModelScope.launch { deviceRepo.delete(device) }
    }

    fun importDevices(text: String) {
        val devices = parseDeviceText(text)
        if (devices.isEmpty()) {
            _importResult.value = ImportResult(0, 0, "未识别到有效设备信息，请检查格式")
            return
        }
        viewModelScope.launch {
            try {
                val ids = deviceRepo.insertAll(devices)
                _importResult.value = ImportResult(devices.size, ids.size, null)
            } catch (e: Exception) {
                _importResult.value = ImportResult(devices.size, 0, e.message)
            }
        }
    }

    fun clearImportResult() { _importResult.value = null }

    private fun parseDeviceText(text: String): List<DeviceEntity> {
        val devices = mutableListOf<DeviceEntity>()
        val line1 = Regex("^(.+?)\\s*\\(([^)]+)\\)")
        val line2 = Regex("DID:\\s*\\d+\\s+Token:\\s*([0-9a-fA-F]{32})")
        val line3 = Regex("IP:\\s*(\\d+\\.\\d+\\.\\d+\\.\\d+)")

        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val m1 = line1.find(lines[i].trim())
            if (m1 != null && i + 2 < lines.size) {
                val name = m1.groupValues[1].trim()
                val model = m1.groupValues[2].trim()
                val m2 = line2.find(lines[i + 1].trim())
                val m3 = line3.find(lines[i + 2].trim())
                if (m2 != null && m3 != null) {
                    val token = m2.groupValues[1]
                    val ip = m3.groupValues[1]
                    devices.add(DeviceEntity(name = name, model = model, ip = ip, token = token))
                    i += 3
                    continue
                }
            }
            i++
        }
        return devices
    }
}

data class ImportResult(
    val parsed: Int,
    val imported: Int,
    val error: String?
)
