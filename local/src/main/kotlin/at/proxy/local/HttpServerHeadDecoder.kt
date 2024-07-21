package at.proxy.local

import com.github.yag.crypto.AESCrypto
import com.google.common.net.HostAndPort
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpConstants
import io.netty.handler.codec.http.HttpMethod
import io.netty.util.ByteProcessor
import io.netty.util.internal.AppendableCharSequence
import ketty.core.client.KettyClient
import java.net.URL

class HttpServerHeadDecoder(private val client: KettyClient, private val crypto: AESCrypto) : SimpleChannelInboundHandler<ByteBuf>() {
    private val headLineByteProcessor = HeadLineByteProcessor()

    private inner class HeadLineByteProcessor : ByteProcessor {
        private val seq: AppendableCharSequence = AppendableCharSequence(4096)

        fun parse(buffer: ByteBuf): AppendableCharSequence? {
            seq.reset()
            val i: Int = buffer.forEachByte(this)
            if (i == -1) {
                return null
            }
            buffer.readerIndex(i + 1)
            return seq
        }

        @Throws(Exception::class)
        override fun process(value: Byte): Boolean {
            val nextByte = (value.toInt() and 0xFF).toChar()
            if (nextByte.code.toByte() == HttpConstants.LF) {
                val len: Int = seq.length
                if (len >= 1 && seq.charAtUnsafe(len - 1).code.toByte() == HttpConstants.CR) {
                    seq.append(nextByte)
                }
                return false
            }
            //continue loop byte
            seq.append(nextByte)
            return true
        }
    }

    @Throws(Exception::class)
    override fun channelRead0(ctx: ChannelHandlerContext, `in`: ByteBuf) {
        val seq: AppendableCharSequence? = headLineByteProcessor.parse(`in`)
        checkNotNull(seq)
        if (seq.last().code.toByte() == HttpConstants.LF) {
            val httpProxyRequestHead: HttpProxyRequestHead
            val splitInitialLine = splitInitialLine(seq)
            val method = splitInitialLine[0]
            val uri = splitInitialLine[1]
            val protocolVersion = splitInitialLine[2]
            val host: String
            var port: Int
            if (HttpMethod.CONNECT.name() == method) {
                //https tunnel proxy
                val hostAndPort: HostAndPort = HostAndPort.fromString(uri)
                host = hostAndPort.getHost()
                port = hostAndPort.getPort()

                httpProxyRequestHead = HttpProxyRequestHead(host, port, HttpProxyType.TUNNEL, protocolVersion, Unpooled.EMPTY_BUFFER)
            } else {
                //http proxy
                val url = URL(uri)
                host = url.host
                port = url.port
                if (port == -1) {
                    port = 80
                }

                httpProxyRequestHead = HttpProxyRequestHead(host, port, HttpProxyType.WEB, protocolVersion, `in`.resetReaderIndex())
            }
            ctx.pipeline().addLast(HttpServerConnectHandler(client, crypto)).remove(this)
            ctx.fireChannelRead(httpProxyRequestHead)
        }
    }

    companion object {
        private fun splitInitialLine(sb: AppendableCharSequence): Array<String> {
            val aEnd: Int
            val bEnd: Int

            val aStart = findNonSPLenient(sb, 0)
            aEnd = findSPLenient(sb, aStart)

            val bStart = findNonSPLenient(sb, aEnd)
            bEnd = findSPLenient(sb, bStart)

            val cStart = findNonSPLenient(sb, bEnd)
            val cEnd = findEndOfString(sb)

            return arrayOf(
                sb.subStringUnsafe(aStart, aEnd),
                sb.subStringUnsafe(bStart, bEnd),
                if (cStart < cEnd) sb.subStringUnsafe(cStart, cEnd) else ""
            )
        }

        private fun findNonSPLenient(sb: AppendableCharSequence, offset: Int): Int {
            for (result in offset until sb.length) {
                val c: Char = sb.charAtUnsafe(result)
                // See https://tools.ietf.org/html/rfc7230#section-3.5
                if (isSPLenient(c)) {
                    continue
                }
                require(!Character.isWhitespace(c)) {
                    // Any other whitespace delimiter is invalid
                    "Invalid separator"
                }
                return result
            }
            return sb.length
        }

        private fun findSPLenient(sb: AppendableCharSequence, offset: Int): Int {
            for (result in offset until sb.length) {
                if (isSPLenient(sb.charAtUnsafe(result))) {
                    return result
                }
            }
            return sb.length
        }

        private fun isSPLenient(c: Char): Boolean {
            // See https://tools.ietf.org/html/rfc7230#section-3.5
            return c == ' ' || c == 0x09.toChar() || c == 0x0B.toChar() || c == 0x0C.toChar() || c == 0x0D.toChar()
        }

        private fun findNonWhitespace(sb: AppendableCharSequence, offset: Int, validateOWS: Boolean): Int {
            for (result in offset until sb.length) {
                val c: Char = sb.charAtUnsafe(result)
                if (!Character.isWhitespace(c)) {
                    return result
                } else require(!(validateOWS && !isOWS(c))) {
                    // Only OWS is supported for whitespace
                    "Invalid separator, only a single space or horizontal tab allowed," +
                            " but received a '" + c + "'"
                }
            }
            return sb.length
        }

        private fun findEndOfString(sb: AppendableCharSequence): Int {
            for (result in sb.length - 1 downTo 1) {
                if (!Character.isWhitespace(sb.charAtUnsafe(result))) {
                    return result + 1
                }
            }
            return 0
        }

        private fun isOWS(ch: Char): Boolean {
            return ch == ' ' || ch == 0x09.toChar()
        }
    }
}
