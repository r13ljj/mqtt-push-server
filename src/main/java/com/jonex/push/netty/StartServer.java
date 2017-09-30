package com.jonex.push.netty;

import io.netty.channel.ChannelFuture;

/**
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/30 10:42
 */
public class StartServer {

    public static void main(String[] args) {
        //启动服务端
        final TcpServer server = new TcpServer();
        ChannelFuture future = server.startServer();

        //钩子
        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run() {
                server.destroy();
            }
        });
        //等待直到有中断
        future.channel().closeFuture().awaitUninterruptibly();

    }
}
