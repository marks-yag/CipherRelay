package at.proxy.local

import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class Socks5Handler(private val remoteAddr: InetSocketAddress) {
    val es: ExecutorService = ThreadPoolExecutor(
        5, 10, 1, TimeUnit.MINUTES, ArrayBlockingQueue(30)
    )
    private val relayHandler = Socks5RelayHandler(remoteAddr)

    fun handle(socket: Socket, allowAnon: Boolean) {
        es.execute {
            try {
                connect(socket, allowAnon)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * The client connects to the server, and sends a version
     * identifier/method selection message:
     *
     * +----+----------+----------+
     * |VER | NMETHODS | METHODS  |
     * +----+----------+----------+
     * | 1  |    1     | 1 to 255 |
     * +----+----------+----------+
     *
     * The VER field is set to X'05' for this version of the protocol.  The
     * NMETHODS field contains the number of method identifier octets that
     * appear in the METHODS field.
     *
     * The server selects from one of the methods given in METHODS, and
     * sends a METHOD selection message:
     *
     * +----+--------+
     * |VER | METHOD |
     * +----+--------+
     * | 1  |   1    |
     * +----+--------+
     *
     * If the selected METHOD is X'FF', none of the methods listed by the
     * client are acceptable, and the client MUST close the connection.
     *
     * The values currently defined for METHOD are:
     * o  X'00' NO AUTHENTICATION REQUIRED
     * o  X'01' GSSAPI
     * o  X'02' USERNAME/PASSWORD
     * o  X'03' to X'7F' IANA ASSIGNED
     * o  X'80' to X'FE' RESERVED FOR PRIVATE METHODS
     * o  X'FF' NO ACCEPTABLE METHODS
     *
     */
    @Throws(IOException::class)
    private fun connect(client: Socket, allowAnon: Boolean) {
        val `is` = client.getInputStream()
        val os = client.getOutputStream()

        /*
         *  +----+----------+----------+
         *  |VER | NMETHODS | METHODS  |
         *  +----+----------+----------+
         *  | 1  |    1     | 1 to 255 |
         *  +----+----------+----------+
         */
        val buffer = ByteArray(257)
        val len = `is`.read(buffer)
        if (len <= 0) {
            os.close()
            return
        }

        //VER
        val version = buffer[0].toInt()
        if (version != 0x05) {
            os.write(byteArrayOf(5, -1))
            return
        }

        //NO AUTHENTICATION REQUIRED
        if (allowAnon) {
            os.write(byteArrayOf(5, 0))
            waitingRequest(client)
            return
        }

        if (len <= 1) {
            os.write(byteArrayOf(5, -1)) //-1 = 0xFF
            return
        }

        //NMETHODS
        val methods = buffer[1].toInt()
        for (i in 0 until methods) {
            //username password authentication
            if (buffer[i + 2].toInt() == 0x02) {
                os.write(byteArrayOf(5, 2))
                if (doAuthentication(client)) {
                    waitingRequest(client)
                }
                return
            }
        }

        os.write(byteArrayOf(5, -1))
    }

    /**
     * Once the method-dependent subnegotiation has completed, the client
     * sends the request details.  If the negotiated method includes
     * encapsulation for purposes of integrity checking and/or
     * confidentiality, these requests MUST be encapsulated in the method-
     * dependent encapsulation.
     *
     * The SOCKS request is formed as follows:
     *
     * +----+-----+-------+------+----------+----------+
     * |VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT |
     * +----+-----+-------+------+----------+----------+
     * | 1  |  1  | X'00' |  1   | Variable |    2     |
     * +----+-----+-------+------+----------+----------+
     *
     * Where:
     *
     * o  VER    protocol version: X'05'
     * o  CMD
     * o  CONNECT X'01'
     * o  BIND X'02'
     * o  UDP ASSOCIATE X'03'
     * o  RSV    RESERVED
     * o  ATYP   address type of following address
     * o  IP V4 address: X'01'
     * o  DOMAINNAME: X'03'
     * o  IP V6 address: X'04'
     * o  DST.ADDR       desired destination address
     * o  DST.PORT desired destination port in network octet order
     *
     * The SOCKS server will typically evaluate the request based on source
     * and destination addresses, and return one or more reply messages, as
     * appropriate for the request type.
     */
    @Throws(IOException::class)
    private fun waitingRequest(socket: Socket) {
        val ins = socket.getInputStream()
        val os = socket.getOutputStream()

        /*
         *   +----+-----+-------+------+----------+----------+
         *   |VER | CMD |  RSV  | ATYP | DST.ADDR | DST.PORT |
         *   +----+-----+-------+------+----------+----------+
         *   | 1  |  1  | X'00' |  1   | Variable |    2     |
         *   +----+-----+-------+------+----------+----------+
         */
        val buffer = ByteArray(256)
        val len = ins.read(buffer)
        if (len <= 0) {
            socket.close()
            return
        }

        val ver = buffer[0].toInt()
        if (ver != 0x05) {
            os.write(byteArrayOf(5, 1, 0, 1, 0, 0, 0, 0, 0))
            return
        }

        val cmd = buffer[1].toInt()
        //ONLY ACCEPT CONNECT
        if (cmd != 0x01) {
            os.write(byteArrayOf(5, 1, 0, 1, 0, 0, 0, 0, 0))
            return
        }

        val targetAddr = getRemoteAddrInfo(buffer, len)
        socket.getOutputStream().write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0))

        relayHandler.doRelay(socket, targetAddr)
    }

    private fun getRemoteAddrInfo(bytes: ByteArray, len: Int): InetSocketAddress {
        val atype = bytes[3]
        val addr = if (atype.toInt() == ATYPE_IPv4) {
            val ipv4 = ByteArray(4)
            System.arraycopy(bytes, 4, ipv4, 0, ipv4.size)
            Inet4Address.getByAddress(ipv4).hostAddress
        } else if (atype.toInt() == ATYPE_IPv6) {
            val ipv6 = ByteArray(16)
            System.arraycopy(bytes, 4, ipv6, 0, ipv6.size)
            Inet6Address.getByAddress(ipv6).hostAddress
        } else if (atype.toInt() == ATYPE_DOMAINNAME) {
            val domainLen = bytes[4].toInt()
            val domain = ByteArray(domainLen)
            System.arraycopy(bytes, 5, domain, 0, domain.size)
            String(domain)
        } else {
            throw IllegalArgumentException("Unknown address type: $atype")
        }

        val buffer = ByteBuffer.wrap(byteArrayOf(bytes[len - 2], bytes[len - 1]))
        val port = buffer.asCharBuffer().get().code

        return InetSocketAddress(addr.trim(), port)
    }

    private class UserInfo {
        var username: String? = null
        var password: String? = null

        fun match(username: String, password: String): Boolean {
            return username == this.username && password == this.password
        }

        companion object {
            fun parse(data: ByteArray): UserInfo {
                val uLen = data[1].toInt()
                val uBytes = ByteArray(uLen)
                System.arraycopy(data, 2, uBytes, 0, uBytes.size)


                val info = UserInfo()
                info.username = String(uBytes)

                val pLen = data[uLen + 2].toInt()
                val pBytes = ByteArray(pLen)
                System.arraycopy(data, uLen + 3, pBytes, 0, pBytes.size)
                info.password = String(pBytes)

                return info
            }
        }
    }

    companion object {
        const val ATYPE_IPv4: Int = 1
        const val ATYPE_DOMAINNAME: Int = 3
        const val ATYPE_IPv6: Int = 4

        /**
         * Once the SOCKS V5 server has started, and the client has selected the
         * Username/Password Authentication protocol, the Username/Password
         * subnegotiation begins.  This begins with the client producing a
         * Username/Password request:
         * +----+------+----------+------+----------+
         * |VER | ULEN |  UNAME   | PLEN |  PASSWD  |
         * +----+------+----------+------+----------+
         * | 1  |  1   | 1 to 255 |  1   | 1 to 255 |
         * +----+------+----------+------+----------+
         *
         * The server verifies the supplied UNAME and PASSWD, and sends the
         * following response:
         *
         * +----+--------+
         * |VER | STATUS |
         * +----+--------+
         * | 1  |   1    |
         * +----+--------+
         *
         * A STATUS field of X'00' indicates success. If the server returns a
         * `failure' (STATUS value other than X'00') status, it MUST close the
         * connection.
         *
         * https://datatracker.ietf.org/doc/html/rfc1929
         */
        @Throws(IOException::class)
        private fun doAuthentication(client: Socket): Boolean {
            val `is` = client.getInputStream()
            val os = client.getOutputStream()
            val buffer = ByteArray(512)
            val len = `is`.read(buffer)
            if (len <= 0) {
                //TODO throw exception
                client.close()
                return false
            }

            val ver = buffer[0].toInt()
            if (ver != 0x01) {
                os.write(byteArrayOf(5, 1))
                return false
            }

            if (len <= 1) {
                os.write(byteArrayOf(5, 1))
                return false
            }

            val info = UserInfo.parse(buffer)

            if (info.match("bigbyto", "123456")) {
                //SUCCESSFUL
                os.write(byteArrayOf(1, 0))
                return true
            }

            //AUTHENTICATION FAILURE
            os.write(byteArrayOf(1, 1))
            return false
        }
    }
}
