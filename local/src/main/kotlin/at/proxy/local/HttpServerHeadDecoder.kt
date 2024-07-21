package at.proxy.local

import com.github.yag.crypto.AESCrypto
import com.google.common.net.HostAndPort
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.http.HttpConstants
import io.netty.handler.codec.http.HttpMethod
import io.netty.util.ByteProcessor
import io.netty.util.internal.AppendableCharSequence
import ketty.core.client.KettyClient
import org.slf4j.LoggerFactory
import java.net.SocketPermission
import java.net.URI
import java.nio.charset.Charset
import java.util.regex.Pattern

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

    override fun channelRead0(ctx: ChannelHandlerContext, buf: ByteBuf) {
        val idx = ByteBufUtil.indexOf(CRLF.slice(), buf)
        check(idx > 0) //TODO what if else
        val data = buf.slice(0, idx)
        val seq = data.toString(Charsets.UTF_8)
        LOG.info("Http read: {}", seq)
        val httpProxyRequestHead: HttpProxyRequestHead
        val (method, uri, protocolVersion) = seq.split(SP)
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
            val url = URI.create(uri).toURL()
            host = url.host
            port = url.port
            if (port == -1) {
                port = 80
            }

            httpProxyRequestHead = HttpProxyRequestHead(host, port, HttpProxyType.WEB, protocolVersion, buf.resetReaderIndex())
        }
        ctx.pipeline().addLast(HttpServerConnectHandler(client, crypto)).remove(this)
        ctx.fireChannelRead(httpProxyRequestHead)
    }

    companion object {

        // See https://tools.ietf.org/html/rfc7230#section-3.5
        private val SP = Pattern.compile("[\u0020\u0009\u000b\u000c\u000d]")

        private val CRLF = Unpooled.wrappedBuffer(byteArrayOf(HttpConstants.CR, HttpConstants.LF))


        private val LOG = LoggerFactory.getLogger(HttpServerConnectHandler::class.java)
    }
}
