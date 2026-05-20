package com.pisces312.milocal.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pisces312.milocal.data.db.AppDatabase
import com.pisces312.milocal.data.db.DeviceEntity
import com.pisces312.milocal.data.repository.DeviceRepository
import com.pisces312.milocal.protocol.MiIoClient
import com.pisces312.milocal.protocol.MiIoProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    val error: String? = null,
    val logs: List<String> = emptyList() // 操作日志
)

class DeviceControlViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DeviceRepository(AppDatabase.getInstance(app).deviceDao())

    private val _state = MutableStateFlow(DeviceControlState())
    val state: StateFlow<DeviceControlState> = _state.asStateFlow()

    /**
     * Mutex 锁：确保同一时刻只有一个协程与设备通信。
     *
     * 为什么需要：
     * MiIoClient 使用同一个 DatagramSocket 收发 UDP 包，不是线程安全的。
     * 如果两个协程同时 send() 和 receive()，响应会错乱（A 发 B 收）。
     * 手机端同时只操作一个设备，用 Mutex 串行化所有设备操作即可。
     */
    private val deviceLock = Mutex()

    /**
     * 设备级 MiIoClient 实例复用。
     *
     * 为什么复用而不是每次 new：
     * 1. MiIoClient 内部维护握手状态（discovered/deviceId/deviceTs），复用可避免每次操作都重新握手，减少延迟。
     * 2. 复用同一个 DatagramSocket，确保同一设备的所有操作使用同一源端口。
     *    某些 Yeelight 设备只回应对应握手源端口的命令，换端口会导致命令被丢弃。
     * 3. 手机端通常只操作一个设备，单实例足够。
     *
     * 切换设备时（IP 或 Token 变化），关闭旧 socket，创建新实例。
     */
    private var miIoClient: MiIoClient? = null
    private var lastIp: String = ""
    private var lastToken: String = ""

    private fun getClient(device: DeviceEntity): MiIoClient {
        val existing = miIoClient
        if (existing != null && device.ip == lastIp && device.token == lastToken) {
            return existing
        }
        existing?.close()
        lastIp = device.ip
        lastToken = device.token
        return MiIoClient(device.ip, device.token).also { miIoClient = it }
    }

    fun loadDevice(deviceId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock {
                val device = repo.getById(deviceId)
                _state.update { it.copy(device = device, logs = emptyList()) }
                device?.let {
                    addLog("加载设备: ${it.name} (${it.ip})")
                    doCheckOnline(it)
                    doFetchStatus(it)
                }
            }
        }
    }

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())
        _state.update { it.copy(logs = it.logs + "[$timestamp] $message") }
    }

    fun checkOnline() {
        val device = _state.value.device ?: return
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock { doCheckOnline(device) }
        }
    }

    private fun doCheckOnline(device: DeviceEntity) {
        _state.update { it.copy(loading = true) }
        try {
            addLog("发送握手包到 ${device.ip}:54321...")
            val client = getClient(device)
            addLog("Token验证: ${client.verifyToken()}")
            val response = client.discover()
            val online = response != null
            addLog("握手结果: ${if (online) "成功 (deviceId=0x${Integer.toHexString(response?.deviceId ?: 0)}, ts=${response?.timestamp})" else "失败 (无响应或超时)"}")
            _state.update { it.copy(online = online, loading = false) }
        } catch (e: Exception) {
            addLog("握手异常: ${e.javaClass.simpleName}: ${e.message}")
            _state.update { it.copy(online = false, loading = false, error = e.message) }
        }
    }

    /**
     * 获取设备当前状态（亮度/色温/开关）。
     */
    fun fetchStatus(device: DeviceEntity? = _state.value.device) {
        device ?: return
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock { doFetchStatus(device) }
        }
    }

    private fun doFetchStatus(device: DeviceEntity) {
        try {
            addLog("查询设备状态...")
            val client = getClient(device)
            if (isYeelight(device)) {
                addLog("使用 Yeelight 旧版协议 get_prop")
                val resp = client.getProp(listOf("power", "bright", "ct"))
                resp?.let {
                    addLog("状态响应: ${it.payload}")
                    parseYeelightStatus(it.payload)
                } ?: addLog("状态查询无响应")
            } else if (isFan(device)) {
                addLog("使用 MIoT 协议 get_properties (风扇)")
                val resp = client.getProperties(2, listOf(1, 2, 3, 7))
                resp?.let {
                    addLog("状态响应: ${it.payload}")
                    parseFanStatus(it.payload)
                } ?: addLog("状态查询无响应")
            } else {
                addLog("使用 MIoT 协议 get_properties")
                val resp = client.getProperties(2, listOf(1, 2, 3))
                resp?.let {
                    addLog("状态响应: ${it.payload}")
                    parseMiotStatus(it.payload)
                } ?: addLog("状态查询无响应")
            }
        } catch (e: Exception) {
            addLog("状态查询异常: ${e.javaClass.simpleName}: ${e.message}")
            _state.update { it.copy(error = e.message) }
        }
    }

    fun togglePower(on: Boolean) {
        val device = _state.value.device ?: return
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock {
                try {
                    addLog("发送开关命令: ${if (on) "开" else "关"}")
                    addLog("设备信息: model=${device.model}, type=${device.type}, ip=${device.ip}")
                    addLog("token前8位: ${device.token.take(8)}...")
                    val client = getClient(device)
                    val result = if (isYeelight(device)) {
                        addLog("使用 Yeelight 协议 set_power [${if (on) "on" else "off"}]")
                        client.setPower(on)
                    } else {
                        addLog("使用 MIoT 协议 set_properties [{siid=2, piid=1, value=$on}]")
                        client.setProperties(listOf(MiIoProperty(2, 1, on)))
                    }
                    result?.let {
                        addLog("响应payload: ${it.payload}")
                        addLog("响应deviceId: 0x${Integer.toHexString(it.deviceId)}, ts: ${it.timestamp}")
                        if (isSuccess(it.payload)) {
                            addLog("开关命令成功")
                            _state.update { s -> s.copy(power = on) }
                        } else {
                            addLog("开关命令失败: ${it.payload}")
                        }
                    } ?: addLog("开关命令无响应 (client返回null)")
                } catch (e: Exception) {
                    addLog("开关异常: ${e.javaClass.simpleName}: ${e.message}")
                    _state.update { it.copy(error = e.message) }
                }
            }
        }
    }

    fun setBrightness(value: Int) {
        val device = _state.value.device ?: return
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock {
                try {
                    val client = getClient(device)
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
                } catch (e: Exception) {
                    addLog("亮度设置异常: ${e.javaClass.simpleName}: ${e.message}")
                    _state.update { it.copy(error = e.message) }
                }
            }
        }
    }

    fun setColorTemp(value: Int) {
        val device = _state.value.device ?: return
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock {
                try {
                    val client = getClient(device)
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
                } catch (e: Exception) {
                    addLog("色温设置异常: ${e.javaClass.simpleName}: ${e.message}")
                    _state.update { it.copy(error = e.message) }
                }
            }
        }
    }

    // 兼容旧的 setProperty API（MIoT 协议）
    fun setProperty(siid: Int, piid: Int, value: Any) {
        val device = _state.value.device ?: return
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock {
                try {
                    val client = getClient(device)
                    val result = client.setProperties(listOf(MiIoProperty(siid, piid, value)))
                    result?.let { parseMiotStatus(it.payload) }
                } catch (e: Exception) {
                    addLog("属性设置异常: ${e.javaClass.simpleName}: ${e.message}")
                    _state.update { it.copy(error = e.message) }
                }
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
            val result = json.optJSONArray("result") ?: return false
            if (result.length() == 0) return false
            // 旧版协议返回 {"result":["ok"],"id":1}
            if (result.optString(0) == "ok") return true
            // MIoT 协议返回 {"result":[{"code":0}],"id":1}
            val first = result.optJSONObject(0)
            first?.optInt("code", -1) == 0
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
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock {
                try {
                    val client = getClient(device)
                    val result = client.setProperties(listOf(MiIoProperty(2, 2, value.coerceIn(1, 100))))
                    result?.let {
                        if (isSuccess(it.payload)) {
                            _state.update { s -> s.copy(fanSpeed = value) }
                        }
                    }
                } catch (e: Exception) {
                    addLog("风速设置异常: ${e.javaClass.simpleName}: ${e.message}")
                    _state.update { it.copy(error = e.message) }
                }
            }
        }
    }

    fun setFanSwing(on: Boolean) {
        val device = _state.value.device ?: return
        if (!isFan(device)) return
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock {
                try {
                    val client = getClient(device)
                    val result = client.setProperties(listOf(MiIoProperty(2, 3, on)))
                    result?.let {
                        if (isSuccess(it.payload)) {
                            _state.update { s -> s.copy(fanSwing = on) }
                        }
                    }
                } catch (e: Exception) {
                    addLog("摇头设置异常: ${e.javaClass.simpleName}: ${e.message}")
                    _state.update { it.copy(error = e.message) }
                }
            }
        }
    }

    fun setFanMode(mode: Int) {
        val device = _state.value.device ?: return
        if (!isFan(device)) return
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock {
                try {
                    val client = getClient(device)
                    val result = client.setProperties(listOf(MiIoProperty(2, 7, mode)))
                    result?.let {
                        if (isSuccess(it.payload)) {
                            _state.update { s -> s.copy(fanMode = mode) }
                        }
                    }
                } catch (e: Exception) {
                    addLog("模式设置异常: ${e.javaClass.simpleName}: ${e.message}")
                    _state.update { it.copy(error = e.message) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            deviceLock.withLock {
                addLog("手动刷新...")
                _state.value.device?.let {
                    doCheckOnline(it)
                    doFetchStatus(it)
                }
            }
        }
    }

    fun clearLogs() {
        _state.update { it.copy(logs = emptyList()) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * ViewModel 销毁时关闭 socket，释放资源。
     * 避免 Activity 重建后留下僵尸 socket 占用端口。
     */
    override fun onCleared() {
        super.onCleared()
        miIoClient?.close()
        miIoClient = null
    }
}
