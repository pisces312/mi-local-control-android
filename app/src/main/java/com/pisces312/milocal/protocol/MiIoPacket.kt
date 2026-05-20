package com.pisces312.milocal.protocol

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * MIoT 协议包构造与解析
 *
 * 包头 32 字节：
 *   [0-1]  magic: 0x2131
 *   [2-3]  length (big-endian)
 *   [4-7]  unknown: 0x00000000
 *   [8-11] device_id (big-endian)
 *   [12-15] timestamp (big-endian)
 *   [16-31] checksum (MD5)
 */
object MiIoPacket {

    const val MAGIC: Int = 0x2131
    const val HEADER_SIZE = 32
    const val DEFAULT_DEVICE_ID = 0xFFFFFFFFL.toInt() // 未知设备ID

    fun build(
        payload: ByteArray,
        token: String,
        deviceId: Int = DEFAULT_DEVICE_ID,
        timestamp: Long = System.currentTimeMillis() / 1000
    ): ByteArray {
        val encrypted = MiIoCrypto.encrypt(payload, token)
        val length = HEADER_SIZE + encrypted.size

        val header = ByteBuffer.allocate(HEADER_SIZE)
        header.putShort(MAGIC.toShort())
        header.putShort(length.toShort())
        header.putInt(0) // unknown
        header.putInt(deviceId)
        header.putInt(timestamp.toInt())

        // checksum = MD5(header_without_checksum + token_bytes + encrypted_data)
        val headerBytes = header.array()
        // token 是 32 位十六进制字符串，解码为 16 字节
        val tokenBytes = token.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        // checksum 只计算 header 前 16 字节（不含 checksum 字段本身），与 python-miio 一致
        val checksumInput = headerBytes.copyOfRange(0, 16) + tokenBytes + encrypted
        val checksum = MessageDigest.getInstance("MD5").digest(checksumInput)
        System.arraycopy(checksum, 0, headerBytes, 16, 16)

        return headerBytes + encrypted
    }

    fun parse(response: ByteArray, token: String): MiIoResponse {
        require(response.size >= HEADER_SIZE) { "Response too short: ${response.size}" }

        val buf = ByteBuffer.wrap(response)
        val magic = buf.short.toInt() and 0xFFFF
        require(magic == MAGIC) { "Invalid magic: $magic" }

        val length = buf.short.toInt() and 0xFFFF
        buf.int // skip unknown
        val deviceId = buf.int
        val timestamp = buf.int

        val encryptedPayload = if (length > HEADER_SIZE) {
            response.copyOfRange(HEADER_SIZE, length)
        } else {
            ByteArray(0)
        }

        // 校验 checksum（仅对有加密 payload 的包校验，握手响应跳过）
        // 与 python-miio IfThenElse(is_hello, Bytes(16), Checksum(...)) 行为一致
        if (encryptedPayload.isNotEmpty()) {
            val tokenBytes = token.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val checksumInput = response.copyOfRange(0, 16) + tokenBytes + encryptedPayload
            val expectedChecksum = MessageDigest.getInstance("MD5").digest(checksumInput)
            val actualChecksum = response.copyOfRange(16, 32)
            if (!expectedChecksum.contentEquals(actualChecksum)) {
                throw IllegalArgumentException("Checksum mismatch: token may be invalid")
            }
        }

        val payload = if (encryptedPayload.isNotEmpty()) {
            MiIoCrypto.decrypt(encryptedPayload, token)
        } else {
            ByteArray(0)
        }

        return MiIoResponse(
            deviceId = deviceId,
            timestamp = timestamp,
            payload = String(payload, Charsets.UTF_8)
        )
    }
}

data class MiIoResponse(
    val deviceId: Int,
    val timestamp: Int,
    val payload: String
)
