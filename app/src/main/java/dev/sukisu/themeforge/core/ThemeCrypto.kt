package dev.sukisu.themeforge.core

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * .sukisu container: MAGIC "SKTH" + 12-byte IV + AES-256-GCM ciphertext.
 *
 * The key is the same public key shipped inside SukiSU Ultra. It is an
 * obfuscation layer for the format, not a secret and not DRM.
 */
object ThemeCrypto {

    private val MAGIC = byteArrayOf(0x53, 0x4B, 0x54, 0x48) // SKTH

    private val KEY: ByteArray = hex(
        "41dc174e5096b4ee93311cc892b132e2" +
            "1961012b14cf2179540f203ca72ab9c1"
    )

    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    fun isPackage(data: ByteArray): Boolean =
        data.size > 4 + IV_LEN + 16 && data.copyOfRange(0, 4).contentEquals(MAGIC)

    fun decrypt(data: ByteArray): ByteArray {
        require(isPackage(data)) { "不是有效的 .sukisu 主题包（缺少 SKTH 标识）" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(KEY, "AES"),
            GCMParameterSpec(TAG_BITS, data, 4, IV_LEN)
        )
        return cipher.doFinal(data, 4 + IV_LEN, data.size - 4 - IV_LEN)
    }

    fun encrypt(zipBytes: ByteArray): ByteArray {
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(KEY, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val cipherText = cipher.doFinal(zipBytes)
        return ByteArrayOutputStream(4 + IV_LEN + cipherText.size).use { out ->
            out.write(MAGIC)
            out.write(iv)
            out.write(cipherText)
            out.toByteArray()
        }
    }

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { i ->
            text.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
}
