module at.proxy.local {
    requires kotlin.stdlib;
    requires io.netty.all;
    requires ketty.core;
    requires at.proxy.protocol;
    
    exports at.proxy.local;
}