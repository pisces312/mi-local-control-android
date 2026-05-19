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
 * 实现参考 python-miio 的 MiIOProtocol：
 * - 发送命令前自动握手获取 device_id 和 timestamp
 * - 时间戳基于设备时间，每次发送递增 1 秒
 * - 握手失败时标记为未连接，下次重试会重新握手
 */
class MiIoClient(
    private val ip: String,
    private val token: String,
    private val timeoutMs: Int = 5000
) {
    companion object {
        private const val TAG = "MiIoClient"
        private const val PORT = 54321
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

    private val socket = DatagramSocket()
    private var discovered = false
    private var deviceId: Int = MiIoPacket.DEFAULT_DEVICE_ID
    private var deviceTs: Int = 0

    init {
        socket.soTimeout = timeoutMs
    }

    /**
     * 发送握手包（hello packet），从设备响应中获取真实的 device_id 和 timestamp。
     * 这是与小米设备通信的前置步骤。
     */
    fun sendHandshake(): MiIoResponse? {
        return try {
            val address = InetAddress.getByName(ip)
            val sendPacket = DatagramPacket(HELLO_BYTES, HELLO_BYTES.size, address, PORT)
            socket.send(sendPacket)

            val receiveBuffer = ByteArray(4096)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            val responseData = receiveBuffer.copyOf(receivePacket.length)
            val resp = MiIoPacket.parse(responseData, token)

            deviceId = resp.deviceId
            deviceTs = resp.timestamp
            discovered = true

            Log.d(TAG, "Handshake success: deviceId=0x${Integer.toHexString(deviceId)}, ts=$deviceTs")
            resp
        } catch (e: Exception) {
            Log.e(TAG, "Handshake failed for $ip: ${e.message}")
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

    private fun sendAndReceive(payloadStr: String): MiIoResponse? {
        return try {
            // 如果未握手，先发送握手包
            if (!discovered) {
                sendHandshake() ?: return null
            }

            // 时间戳基于设备时间，每次发送递增 1 秒（与 python-miio 一致）
            deviceTs += 1

            val data = MiIoPacket.build(
                payloadStr.toByteArray(Charsets.UTF_8),
                token,
                deviceId,
                deviceTs.toLong()
            )
            val address = InetAddress.getByName(ip)

            val sendPacket = DatagramPacket(data, data.size, address, PORT)
            socket.send(sendPacket)

            val receiveBuffer = ByteArray(4096)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            val responseData = receiveBuffer.copyOf(receivePacket.length)
            val resp = MiIoPacket.parse(responseData, token)

            // 更新本地时间戳为设备返回的时间戳
            deviceTs = resp.timestamp

            resp
        } catch (e: Exception) {
            Log.e(TAG, "Send/receive failed for $ip: ${e.message}")
            // 失败后重置 discovered，下次重试会重新握手
            discovered = false
            null
        }
    }

    fun close() {
        socket.close()
    }
}

data class MiIoProperty(
    val siid: Int,
    val piid: Int,
    val value: Any
)
