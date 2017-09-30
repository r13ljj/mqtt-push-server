package com.jonex.push.netty;

import com.jonex.push.protocol.mqtt.MQTTDecoder;
import com.jonex.push.protocol.mqtt.MQTTEncoder;
import com.jonex.push.protocol.mqtt.MQTTMessageHandler;
import com.jonex.push.util.MqttProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

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

    public ChannelFuture startServer(){
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("MQTTDecoder", new MQTTDecoder());
                        ch.pipeline().addLast("MQTTEncoder", new MQTTEncoder());
                        ch.pipeline().addLast("MQTTMessageHandler", new MQTTMessageHandler());
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 1024)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        ChannelFuture future = null;
        try{
            future = bootstrap.bind(new InetSocketAddress(port)).sync();
            channel = future.channel();
        }catch(Exception e){
            LOG.error("TcpServer start exception:", e);
        }
        return future;
    }

    public void destroy(){
        if (channel != null) {
            channel.close();
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
