package com.pisces312.milocal.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pisces312.milocal.data.db.AppDatabase
import com.pisces312.milocal.data.db.DeviceEntity
import com.pisces312.milocal.data.repository.DeviceRepository
import com.pisces312.milocal.protocol.MiIoClient
import com.pisces312.milocal.protocol.MiIoProperty
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

data class DeviceControlState(
    val device: DeviceEntity? = null,
    val online: Boolean? = null,
    val properties: Map<String, Any> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null
)

class DeviceControlViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DeviceRepository(AppDatabase.getInstance(app).deviceDao())

    private val _state = MutableStateFlow(DeviceControlState())
    val state: StateFlow<DeviceControlState> = _state.asStateFlow()

    fun loadDevice(deviceId: Long) {
        viewModelScope.launch {
            val device = repo.getById(deviceId)
            _state.update { it.copy(device = device) }
            device?.let { checkOnline(it) }
        }
    }

    private fun checkOnline(device: DeviceEntity) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val client = MiIoClient(device.ip, device.token)
                val response = client.discover()
                _state.update { it.copy(online = response != null, loading = false) }
                if (response != null) {
                    parseProperties(response.payload)
                }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(online = false, loading = false, error = e.message) }
            }
        }
    }

    fun setProperty(siid: Int, piid: Int, value: Any) {
        val device = _state.value.device ?: return
        viewModelScope.launch {
            try {
                val client = MiIoClient(device.ip, device.token)
                val result = client.setProperties(listOf(MiIoProperty(siid, piid, value)))
                result?.let { parseProperties(it.payload) }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun parseProperties(payload: String) {
        if (payload.isBlank()) return
        try {
            val json = JSONObject(payload)
            val result = json.optJSONArray("result") ?: return
            val props = mutableMapOf<String, Any>()
            for (i in 0 until result.length()) {
                val item = result.getJSONObject(i)
                val key = "s${item.optInt("siid")}_p${item.optInt("piid")}"
                item.opt("value")?.let { props[key] = it }
            }
            _state.update { it.copy(properties = props) }
        } catch (_: Exception) {}
    }

    fun refresh() {
        _state.value.device?.let { checkOnline(it) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
