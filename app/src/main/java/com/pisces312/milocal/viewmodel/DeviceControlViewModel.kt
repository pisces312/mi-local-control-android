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
import org.json.JSONArray
import org.json.JSONObject

data class DeviceControlState(
    val device: DeviceEntity? = null,
    val online: Boolean? = null,
    val power: Boolean = false,
    val brightness: Int = 50,
    val colorTemp: Int = 4000,
    val fanSpeed: Int = 50,
    val fanSwing: Boolean = false,
    val fanMode: Int = 0, // 0=直吹, 1=自然风
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
            device?.let {
                checkOnline(it)
                fetchStatus(it)
            }
        }
    }

    private fun checkOnline(device: DeviceEntity) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val client = MiIoClient(device.ip, device.token)
                val response = client.discover()
                _state.update { it.copy(online = response != null, loading = false) }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(online = false, loading = false, error = e.message) }
            }
        }
    }

    /**
     * 获取设备当前状态（亮度/色温/开关）。
     */
    fun fetchStatus(device: DeviceEntity? = _state.value.device) {
        device ?: return
        viewModelScope.launch {
            try {
                val client = MiIoClient(device.ip, device.token)
                if (isYeelight(device)) {
                    val resp = client.getProp(listOf("power", "bright", "ct"))
                    resp?.let { parseYeelightStatus(it.payload) }
                } else if (isFan(device)) {
                    val resp = client.getProperties(2, listOf(1, 2, 3, 7))
                    resp?.let { parseFanStatus(it.payload) }
                } else {
                    val resp = client.getProperties(2, listOf(1, 2, 3))
                    resp?.let { parseMiotStatus(it.payload) }
                }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun togglePower(on: Boolean) {
        val device = _state.value.device ?: return
        viewModelScope.launch {
            try {
                val client = MiIoClient(device.ip, device.token)
                val result = if (isYeelight(device)) {
                    client.setPower(on)
                } else {
                    client.setProperties(listOf(MiIoProperty(2, 1, on)))
                }
                result?.let {
                    if (isSuccess(it.payload)) {
                        _state.update { s -> s.copy(power = on) }
                    }
                }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setBrightness(value: Int) {
        val device = _state.value.device ?: return
        viewModelScope.launch {
            try {
                val client = MiIoClient(device.ip, device.token)
                val result = if (isYeelight(device)) {
                    client.setBright(value.coerceIn(1, 100))
                } else {
                    client.setProperties(listOf(MiIoProperty(2, 2, value)))
                }
                result?.let {
                    if (isSuccess(it.payload)) {
                        _state.update { s -> s.copy(brightness = value) }
                    }
                }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setColorTemp(value: Int) {
        val device = _state.value.device ?: return
        viewModelScope.launch {
            try {
                val client = MiIoClient(device.ip, device.token)
                val result = if (isYeelight(device)) {
                    client.setCtAbx(value.coerceIn(2700, 6500))
                } else {
                    client.setProperties(listOf(MiIoProperty(2, 3, value)))
                }
                result?.let {
                    if (isSuccess(it.payload)) {
                        _state.update { s -> s.copy(colorTemp = value) }
                    }
                }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // 兼容旧的 setProperty API（MIoT 协议）
    fun setProperty(siid: Int, piid: Int, value: Any) {
        val device = _state.value.device ?: return
        viewModelScope.launch {
            try {
                val client = MiIoClient(device.ip, device.token)
                val result = client.setProperties(listOf(MiIoProperty(siid, piid, value)))
                result?.let { parseMiotStatus(it.payload) }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun isYeelight(device: DeviceEntity): Boolean {
        return device.model.startsWith("yeelink.")
    }

    private fun isFan(device: DeviceEntity): Boolean {
        return device.type == "fan" || device.model.startsWith("zhimi.fan.")
    }

    private fun isSuccess(payload: String): Boolean {
        if (payload.isBlank()) return false
        return try {
            val json = JSONObject(payload)
            // 旧版协议返回 {"result":["ok"],"id":1}
            val result = json.optJSONArray("result")
            if (result != null && result.length() > 0) {
                result.optString(0) == "ok"
            } else {
                // MIoT 协议返回 {"result":[{"code":0}],"id":1}
                val first = result?.optJSONObject(0)
                first?.optInt("code", -1) == 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseYeelightStatus(payload: String) {
        if (payload.isBlank()) return
        try {
            val json = JSONObject(payload)
            val result = json.optJSONArray("result") ?: return
            val power = result.optString(0, "off") == "on"
            val brightness = result.optString(1, "50").toIntOrNull() ?: 50
            val ct = result.optString(2, "4000").toIntOrNull() ?: 4000
            _state.update { it.copy(power = power, brightness = brightness, colorTemp = ct) }
        } catch (_: Exception) {}
    }

    private fun parseMiotStatus(payload: String) {
        if (payload.isBlank()) return
        try {
            val json = JSONObject(payload)
            val result = json.optJSONArray("result") ?: return
            var power = false
            var brightness = 50
            var ct = 4000
            for (i in 0 until result.length()) {
                val item = result.getJSONObject(i)
                val siid = item.optInt("siid")
                val piid = item.optInt("piid")
                val value = item.opt("value")
                if (siid == 2) {
                    when (piid) {
                        1 -> power = value == true || value.toString() == "true"
                        2 -> brightness = (value as? Number)?.toInt() ?: 50
                        3 -> ct = (value as? Number)?.toInt() ?: 4000
                    }
                }
            }
            _state.update { it.copy(power = power, brightness = brightness, colorTemp = ct) }
        } catch (_: Exception) {}
    }

    private fun parseFanStatus(payload: String) {
        if (payload.isBlank()) return
        try {
            val json = JSONObject(payload)
            val result = json.optJSONArray("result") ?: return
            var power = false
            var speed = 50
            var swing = false
            var mode = 0
            for (i in 0 until result.length()) {
                val item = result.getJSONObject(i)
                val siid = item.optInt("siid")
                val piid = item.optInt("piid")
                val value = item.opt("value")
                if (siid == 2) {
                    when (piid) {
                        1 -> power = value == true || value.toString() == "true"
                        2 -> speed = (value as? Number)?.toInt() ?: 50
                        3 -> swing = value == true || value.toString() == "true"
                        7 -> mode = (value as? Number)?.toInt() ?: 0
                    }
                }
            }
            _state.update { it.copy(power = power, fanSpeed = speed, fanSwing = swing, fanMode = mode) }
        } catch (_: Exception) {}
    }

    // ---------- 风扇控制 ----------

    fun setFanSpeed(value: Int) {
        val device = _state.value.device ?: return
        if (!isFan(device)) return
        viewModelScope.launch {
            try {
                val client = MiIoClient(device.ip, device.token)
                val result = client.setProperties(listOf(MiIoProperty(2, 2, value.coerceIn(1, 100))))
                result?.let {
                    if (isSuccess(it.payload)) {
                        _state.update { s -> s.copy(fanSpeed = value) }
                    }
                }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setFanSwing(on: Boolean) {
        val device = _state.value.device ?: return
        if (!isFan(device)) return
        viewModelScope.launch {
            try {
                val client = MiIoClient(device.ip, device.token)
                val result = client.setProperties(listOf(MiIoProperty(2, 3, on)))
                result?.let {
                    if (isSuccess(it.payload)) {
                        _state.update { s -> s.copy(fanSwing = on) }
                    }
                }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun setFanMode(mode: Int) {
        val device = _state.value.device ?: return
        if (!isFan(device)) return
        viewModelScope.launch {
            try {
                val client = MiIoClient(device.ip, device.token)
                val result = client.setProperties(listOf(MiIoProperty(2, 7, mode)))
                result?.let {
                    if (isSuccess(it.payload)) {
                        _state.update { s -> s.copy(fanMode = mode) }
                    }
                }
                client.close()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun refresh() {
        _state.value.device?.let {
            checkOnline(it)
            fetchStatus(it)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
