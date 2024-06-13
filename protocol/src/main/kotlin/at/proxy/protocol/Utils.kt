package at.proxy.protocol

import org.apache.commons.lang3.ObjectUtils.Null
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketException
import java.nio.charset.Charset
import java.security.SecureRandom
import javax.crypto.*
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

private const val KEY = "hello1111111111111111111"

val iv = "0123456789abcdef".toByteArray()


object Utils {

    fun getEncryptCipher(): Cipher {
        val key = SecretKeySpec(KEY.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    fun getDecryptCipher(): Cipher {
        val key = SecretKeySpec(KEY.toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        return cipher
    }

    fun doRelay(id: String, type: Type, from: SocketWithStream, to: SocketWithStream) =
        thread(name = "$from->$to") {
            try {
                val copied = from.ins.myCopyTo(to.os)
                to.os.flush()
                println("Copied ${copied.size} bytes: ${copied.decodeToString()}")
                File("traffic-$id-$type").writeBytes(copied)
            } finally {
                to.os.close()
                if (!from.socket.isClosed) {
                    from.socket.close()
                }
                if (!to.socket.isClosed) {
                    to.socket.shutdownInput()
                }
            }
        }

    fun InputStream.myCopyTo(out: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): ByteArray {
        var bytesCopied: Long = 0
        val buffer = ByteArray(bufferSize)
        var bytes = try {
            read(buffer)
        } catch (e: Exception) {
            -1
        }
        val copy = ByteArrayOutputStream()
        while (bytes >= 0) {
            out.write(buffer, 0, bytes)
            copy.write(buffer, 0, bytes)
            bytesCopied += bytes
            bytes = try {
                read(buffer)
            } catch (e: Exception) {
                -1
            }
        }
        return copy.toByteArray()
    }

    var safe = false

    fun cipherOutputStream(client: Socket) = if (safe) client.getOutputStream()
        else CipherOutputStream(client.getOutputStream(), getEncryptCipher())

    fun cipherInputStream(client: Socket) = if (safe) client.getInputStream()
        else CipherInputStream(client.getInputStream(), getDecryptCipher())
}