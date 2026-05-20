package com.pisces312.milocal.protocol

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * MIoT 协议加解密工具
 * key = MD5(token), iv = MD5(MD5(token) + token)
 */
object MiIoCrypto {

    fun md5(data: ByteArray): ByteArray {
        return MessageDigest.getInstance("MD5").digest(data)
    }

    fun md5Hex(data: ByteArray): String {
        return md5(data).joinToString("") { "%02x".format(it) }
    }

    fun deriveKey(token: String): ByteArray {
        // token 是 32 位十六进制字符串，先解码为 16 字节
        val tokenBytes = token.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return md5(tokenBytes)
    }

    fun deriveIv(token: String): ByteArray {
        val key = deriveKey(token)
        val tokenBytes = token.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val ivInput = key + tokenBytes
        return md5(ivInput)
    }

    fun encrypt(plaintext: ByteArray, token: String): ByteArray {
        val key = deriveKey(token)
        val iv = deriveIv(token)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        // python-miio 在加密前会在 JSON payload 末尾追加 \x00
        val padded = plaintext + byteArrayOf(0x00)
        android.util.Log.d("MiIoCrypto", "[加密] payload=${plaintext.toString(Charsets.UTF_8)}, key=${key.joinToString("") { "%02x".format(it) }}, iv=${iv.joinToString("") { "%02x".format(it) }}")
        return cipher.doFinal(padded)
    }

    fun decrypt(ciphertext: ByteArray, token: String): ByteArray {
        val key = deriveKey(token)
        val iv = deriveIv(token)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val decrypted = cipher.doFinal(ciphertext)
        // 去除 python-miio 添加的末尾 \x00
        var end = decrypted.size
        while (end > 0 && decrypted[end - 1] == 0x00.toByte()) {
            end--
        }
        val result = decrypted.copyOfRange(0, end)
        return result
    }

    /** 调试用：验证token解码和密钥推导 */
    fun verifyToken(token: String): String {
        val tokenBytes = token.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val key = deriveKey(token)
        val iv = deriveIv(token)
        return "tokenBytes=${tokenBytes.size}字节, key=${key.joinToString("") { "%02x".format(it) }}, iv=${iv.joinToString("") { "%02x".format(it) }}"
    }
}
