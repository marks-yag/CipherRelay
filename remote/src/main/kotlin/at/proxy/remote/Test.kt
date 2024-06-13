package at.proxy.remote

import at.proxy.protocol.Utils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream

object Test {

    @JvmStatic
    fun main(args: Array<String>) {
        val bytes = File("traffic-33938221-0758-4a66-bcb8-c4ac932c6e3b-OUT").readBytes()
        val bais = ByteArrayInputStream(bytes)
        val cipher = Utils.getEncryptCipher()
        val dcipher = Utils.getDecryptCipher()
        val baos = ByteArrayOutputStream()
        val cbaos = CipherOutputStream(baos, cipher)
        bais.copyTo(cbaos, 10)
        //cbaos.close()
        println(baos.toByteArray().contentToString())

        val cbais = CipherInputStream(ByteArrayInputStream(baos.toByteArray()), dcipher)
        println(cbais.readAllBytes().decodeToString())
    }
}