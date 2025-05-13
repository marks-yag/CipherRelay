module com.github.yag.cr.local {
    requires kotlin.stdlib;
    requires org.slf4j;
    requires io.netty.buffer;
    requires io.netty.codec;
    requires io.netty.codec.http;
    requires io.netty.codec.socks;
    requires io.netty.common;
    requires io.netty.handler;
    requires io.netty.transport;
    
    requires com.github.yag.cr.protocol;
    
    exports com.github.yag.cr.local;
}