package com.pisces312.milocal.protocol

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * MIoT 客户端：通过 UDP 54321 端口与小米设备通信
 */
class MiIoClient(
    private val ip: String,
    private val token: String,
    private val deviceId: Int = MiIoPacket.DEFAULT_DEVICE_ID,
    private val timeoutMs: Int = 5000
) {
    companion object {
        private const val TAG = "MiIoClient"
        private const val PORT = 54321
        private val idCounter = AtomicInteger(1)
    }

    private val socket = DatagramSocket()

    init {
        socket.soTimeout = timeoutMs
    }

    fun discover(): MiIoResponse? {
        return try {
            // 发送空 payload 的发现包
            val payload = """{"id":${idCounter.getAndIncrement()},"method":"miIO.info","params":[]}"""
            sendAndReceive(payload)
        } catch (e: Exception) {
            Log.e(TAG, "Discover failed: ${e.message}")
            null
        }
    }

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

    private fun sendAndReceive(payloadStr: String): MiIoResponse? {
        return try {
            val data = MiIoPacket.build(payloadStr.toByteArray(Charsets.UTF_8), token, deviceId)
            val address = InetAddress.getByName(ip)

            // 发送
            val sendPacket = DatagramPacket(data, data.size, address, PORT)
            socket.send(sendPacket)

            // 接收
            val receiveBuffer = ByteArray(4096)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            socket.receive(receivePacket)

            val responseData = receiveBuffer.copyOf(receivePacket.length)
            MiIoPacket.parse(responseData, token)
        } catch (e: Exception) {
            Log.e(TAG, "Send/receive failed for $ip: ${e.message}")
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
