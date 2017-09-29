package com.jonex.push.netty;

import com.jonex.push.util.MqttProperties;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/29 18:17
 */
public class TcpServer {

    private final static Logger LOG = LoggerFactory.getLogger(TcpServer.class);

    private final static String KEY_PORT = "port";

    private Integer port;
    private Channel channel;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public TcpServer(){
        this.port = MqttProperties.getPropertyToInt(KEY_PORT);
        if (this.port == null) {
            this.port = 8088;
        }
    }

    public void startServer(){

    }

    public void destroy(){

    }
}
