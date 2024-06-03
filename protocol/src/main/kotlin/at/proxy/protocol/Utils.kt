package at.proxy.protocol

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object Utils {

    fun getEncryptCipher(): Cipher {
        val key = SecretKeySpec("hello".toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    fun getDecryptCipher(): Cipher {
        val key = SecretKeySpec("hello".toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, key)
        return cipher
    }
}