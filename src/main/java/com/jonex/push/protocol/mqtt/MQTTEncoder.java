package com.jonex.push.protocol.mqtt;

import com.jonex.push.protocol.mqtt.message.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:37
 */
public class MQTTEncoder extends MessageToByteEncoder<Message> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {

    }
}
