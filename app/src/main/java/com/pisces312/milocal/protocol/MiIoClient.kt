package com.pisces312.milocal.protocol

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * MIoT 客户端：通过 UDP 54321 端口与小米设备通信。
 * 支持 MIoT 协议（set_properties/get_properties）和旧版 miIO 协议（set_power 等）。
 *
 * 设计原则（与 python-miio 的 MiIOProtocol 一致）：
 * 1. 每次 sendAndReceive 新建 DatagramSocket，命令完成后关闭。
 *   与 python-miio 行为一致：每次 send() 创建新 socket，获得新的临时端口。
 * 2. 握手包发送 3 次（与 python-miio discover() 一致），提高局域网 UDP 可靠性。
 * 3. 失败自动重试 3 次，每次重试递增 id 并重新握手（与 python-miio 一致）。
 * 4. 握手状态（deviceId、deviceTs）跨调用保持，避免每次命令都重新握手。
 * 5. 时间戳基于设备时间，每次发送递增 1 秒，与 python-miio 一致。
 */
class MiIoClient(
    private val ip: String,
    private val token: String,
    private val timeoutMs: Int = 5000
) {
    companion object {
        private const val TAG = "MiIoClient"
        private const val PORT = 54321
        private const val MAX_RETRY = 3
        private val idCounter = AtomicInteger(1)

        /**
         * 标准握手包（hello packet）：32 字节固定长度。
         * 2131 0020 ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
         */
        private val HELLO_BYTES = byteArrayOf(
            0x21, 0x31, 0x00, 0x20,
            0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
            0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte()
        )
    }

    private var discovered = false
    private var deviceId: Int = MiIoPacket.DEFAULT_DEVICE_ID
    private var deviceTs: Int = 0

    /**
     * 发送握手包（hello packet），从设备响应中获取真实的 device_id 和 timestamp。
     * 握手包发送 3 次（与 python-miio discover() 一致），提高 UDP 可靠性。
     * 每次握手使用独立的 DatagramSocket。
     */
    fun sendHandshake(): MiIoResponse? {
        return try {
            Log.d(TAG, "[握手] 发送hello包到 $ip:$PORT")
            val address = InetAddress.getByName(ip)
            val socket = DatagramSocket()
            try {
                socket.soTimeout = timeoutMs

                // 发送 3 次握手包，与 python-miio 一致
                for (i in 1..3) {
                    val sendPacket = DatagramPacket(HELLO_BYTES, HELLO_BYTES.size, address, PORT)
                    socket.send(sendPacket)
                }

                val receiveBuffer = ByteArray(4096)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                Log.d(TAG, "[握手] 等待响应...")
                socket.receive(receivePacket)

                val responseData = receiveBuffer.copyOf(receivePacket.length)
                Log.d(TAG, "[握手] 收到 ${responseData.size} 字节")
                val resp = MiIoPacket.parse(responseData, token)

                deviceId = resp.deviceId
                deviceTs = resp.timestamp
                discovered = true

                Log.d(TAG, "[握手] 成功: deviceId=0x${Integer.toHexString(deviceId)}, ts=$deviceTs")
                resp
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[握手] 失败 $ip: ${e.javaClass.simpleName}: ${e.message}")
            discovered = false
            null
        }
    }

    /**
     * 设备发现：发送标准握手包并返回响应。
     */
    fun discover(): MiIoResponse? {
        return sendHandshake()
    }

    /**
     * 通用发送接口：支持任意 method 和 params（JSONArray 格式，用于旧版协议）。
     */
    fun send(method: String, params: JSONArray): MiIoResponse? {
        val payload = JSONObject().apply {
            put("id", idCounter.getAndIncrement())
            put("method", method)
            put("params", params)
        }.toString()
        return sendAndReceive(payload)
    }

    // ---------- MIoT 协议 ----------

    fun getProperties(siid: Int, piids: List<Int>): MiIoResponse? {
        val params = JSONArray().apply {
            for (piid in piids) {
                put(JSONObject().apply {
                    put("did", "")
                    put("siid", siid)
                    put("piid", piid)
                })
            }
        }
        val payload = JSONObject().apply {
            put("id", idCounter.getAndIncrement())
            put("method", "get_properties")
            put("params", params)
        }.toString()
        return sendAndReceive(payload)
    }

    fun setProperties(properties: List<MiIoProperty>): MiIoResponse? {
        val params = JSONArray().apply {
            for (prop in properties) {
                put(JSONObject().apply {
                    put("did", "")
                    put("siid", prop.siid)
                    put("piid", prop.piid)
                    put("value", prop.value)
                })
            }
        }
        val payload = JSONObject().apply {
            put("id", idCounter.getAndIncrement())
            put("method", "set_properties")
            put("params", params)
        }.toString()
        return sendAndReceive(payload)
    }

    fun action(siid: Int, aiid: Int, inParams: JSONObject = JSONObject()): MiIoResponse? {
        val payload = JSONObject().apply {
            put("id", idCounter.getAndIncrement())
            put("method", "action")
            put("params", JSONObject().apply {
                put("did", "")
                put("siid", siid)
                put("aiid", aiid)
                put("in", inParams)
            })
        }.toString()
        return sendAndReceive(payload)
    }

    // ---------- 旧版 Yeelight / miIO 协议 ----------

    /** 开关灯：params = ["on"] 或 ["off"] */
    fun setPower(on: Boolean): MiIoResponse? {
        return send("set_power", JSONArray().apply { put(if (on) "on" else "off") })
    }

    /** 设置亮度：params = [value] (1-100) */
    fun setBright(value: Int): MiIoResponse? {
        return send("set_bright", JSONArray().apply { put(value) })
    }

    /** 设置色温：params = [value] (2700-6500) */
    fun setCtAbx(value: Int): MiIoResponse? {
        return send("set_ct_abx", JSONArray().apply { put(value) })
    }

    /** 获取属性：params = ["power", "bright", "ct"] */
    fun getProp(props: List<String>): MiIoResponse? {
        return send("get_prop", JSONArray().apply {
            for (p in props) put(p)
        })
    }

    /**
     * 发送命令并等待响应，带自动重试。
     *
     * 流程（与 python-miio MiIOProtocol.send() 一致）：
     * 1. 若未握手（discovered=false），先发送握手包获取 deviceId 和 timestamp。
     * 2. 新建 DatagramSocket，发送命令包，阻塞等待响应。
     * 3. 解析响应，更新本地时间戳为设备返回的时间戳。
     * 4. 失败时自动重试（最多 MAX_RETRY 次），每次重试递增 id 并重新握手。
     */
    private fun sendAndReceive(payloadStr: String, retryCount: Int = MAX_RETRY): MiIoResponse? {
        return try {
            // 如果未握手，先发送握手包
            if (!discovered) {
                Log.d(TAG, "[发送] 未握手，先握手...")
                sendHandshake() ?: return null
            }

            // 时间戳基于设备时间，每次发送递增 1 秒（与 python-miio 一致）
            deviceTs += 1

            Log.d(TAG, "[发送] method=${payloadStr.substringAfter("\"method\":\"").substringBefore("\"")}, id=${payloadStr.substringAfter("\"id\":").substringBefore(",")}")
            val data = MiIoPacket.build(
                payloadStr.toByteArray(Charsets.UTF_8),
                token,
                deviceId,
                deviceTs.toLong()
            )
            val address = InetAddress.getByName(ip)

            // 每次命令新建 socket，与 python-miio 一致
            val socket = DatagramSocket()
            try {
                socket.soTimeout = timeoutMs

                val sendPacket = DatagramPacket(data, data.size, address, PORT)
                socket.send(sendPacket)
                Log.d(TAG, "[发送] 已发送 ${data.size} 字节")

                val receiveBuffer = ByteArray(4096)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                Log.d(TAG, "[发送] 等待响应...")
                socket.receive(receivePacket)

                val responseData = receiveBuffer.copyOf(receivePacket.length)
                Log.d(TAG, "[发送] 收到 ${responseData.size} 字节")
                val resp = MiIoPacket.parse(responseData, token)

                // 更新本地时间戳为设备返回的时间戳，保持与设备时间同步
                deviceTs = resp.timestamp

                resp
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[发送] 失败 $ip (retry=${MAX_RETRY - retryCount}): ${e.javaClass.simpleName}: ${e.message}")
            // 与 python-miio 一致：失败后递增 id 并重新握手，然后重试
            if (retryCount > 0) {
                idCounter.addAndGet(100)
                discovered = false
                sendAndReceive(payloadStr, retryCount - 1)
            } else {
                discovered = false
                null
            }
        }
    }

    /** 调试用：验证token和加密 */
    fun verifyToken(): String {
        return MiIoCrypto.verifyToken(token)
    }

    fun close() {
        // 不再持有长生命周期 socket，无需关闭
    }
}

data class MiIoProperty(
    val siid: Int,
    val piid: Int,
    val value: Any
)
