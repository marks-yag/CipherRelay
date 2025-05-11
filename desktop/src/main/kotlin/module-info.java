module at.proxy.desktop {
    requires kotlin.stdlib;
    requires java.desktop;
    requires com.fasterxml.jackson.databind;
    requires org.slf4j;
    requires at.proxy.local;
    requires com.formdev.flatlaf;
    
    opens at.proxy.desktop to com.fasterxml.jackson.databind;
}