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
        return md5(token.toByteArray())
    }

    fun deriveIv(token: String): ByteArray {
        val key = deriveKey(token)
        val ivInput = key + token.toByteArray()
        return md5(ivInput)
    }

    fun encrypt(plaintext: ByteArray, token: String): ByteArray {
        val key = deriveKey(token)
        val iv = deriveIv(token)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plaintext)
    }

    fun decrypt(ciphertext: ByteArray, token: String): ByteArray {
        val key = deriveKey(token)
        val iv = deriveIv(token)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ciphertext)
    }
}
