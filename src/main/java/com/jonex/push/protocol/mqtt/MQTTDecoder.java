package com.jonex.push.protocol.mqtt;

import com.jonex.push.protocol.mqtt.message.FixedHeader;
import com.jonex.push.protocol.mqtt.message.MessageType;
import com.jonex.push.protocol.mqtt.message.Qos;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.ReplayingDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * MQTT协议解码：使用netty的ReplayingState来进行解码，该类使用状态机的方式防止代码的反复执行
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public class MQTTDecoder extends ReplayingDecoder<MQTTDecoder.DecoderState> {

    private final static Logger LOG = LoggerFactory.getLogger(MQTTDecoder.class);

    enum DecoderState{
        FIXED_HEADER,
        VARIABLE_HEADER,
        PAYLOAD,
        BAD_MESSAGE,
    }

    private FixedHeader fixedHeader;
    private Object variableHeader;
    private Object payload;

    public MQTTDecoder() {
        super(DecoderState.FIXED_HEADER);
    }


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        int bytesRemainingInVariablePart = 0;
        switch (state()){
            case FIXED_HEADER:
                fixedHeader = decodeFixedHeader(in);
                bytesRemainingInVariablePart = fixedHeader.getMessageLength();
                checkpoint(DecoderState.VARIABLE_HEADER);
            case VARIABLE_HEADER:
            case PAYLOAD:
            case BAD_MESSAGE:
        }
    }

    /**
     * 解压缩固定头部，mqtt协议的所有消息类型，固定头部字段类型和长度都是一样的。
     *
     * @param byteBuf
     * @return FixedHeader
     */
    private FixedHeader decodeFixedHeader(ByteBuf byteBuf){
        //解码头部第一个字节
        byte headerData = byteBuf.readByte();
        MessageType type = MessageType.valueOf((headerData >> 4) & 0x0F);
        boolean dup = (headerData & 0x08) > 0;
        Qos qos = Qos.valueOf((headerData & 0x06) >> 1);
        boolean retain = (headerData & 0x01) > 0;

        //解码头部第二个字节，余留长度
        int multiplier = 1;
        int remainLength = 0;
        byte digit = 0;
        int loop = 0;
        do {
            digit = byteBuf.readByte();
            remainLength += (digit & 0x7f) * multiplier;
            multiplier *= 128;
            loop++;
        }while ((digit & 0x80) != 0 && loop < 4);

        if (loop == 4 && (digit & 0x80) != 0) {
            throw new DecoderException("保留字段长度超过4个字节，与协议不符，消息类型:" + type);
        }

        FixedHeader fixedHeader = new FixedHeader(type, dup, qos, retain, remainLength);
        //返回时，针对所有协议进行头部校验
        return validateFixHeader(fixedHeader);
    }

    private FixedHeader validateFixHeader(FixedHeader fixedHeader){
        switch (fixedHeader.getMessageType()) {
            case PUBREL:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
                if (fixedHeader.getQos() != Qos.AT_LEAST_ONCE) {
                    throw new DecoderException(fixedHeader.getMessageType().name()+"的Qos必须为1");
                }

                if (fixedHeader.isDup()) {
                    throw new DecoderException(fixedHeader.getMessageType().name()+"的Dup必须为0");
                }

                if (fixedHeader.isRetain()) {
                    throw new DecoderException(fixedHeader.getMessageType().name()+"的Retain必须为0");
                }
                break;
            case CONNECT:
            case CONNACK:
            case PUBACK:
            case PUBREC:
            case PUBCOMP:
            case SUBACK:
            case UNSUBACK:
            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                if (fixedHeader.getQos() != Qos.AT_MOST_ONCE) {
                    throw new DecoderException(fixedHeader.getMessageType().name()+"的Qos必须为0");
                }

                if (fixedHeader.isDup()) {
                    throw new DecoderException(fixedHeader.getMessageType().name()+"的Dup必须为0");
                }

                if (fixedHeader.isRetain()) {
                    throw new DecoderException(fixedHeader.getMessageType().name()+"的Retain必须为0");
                }
                break;
            default:
                return fixedHeader;
        }
        return fixedHeader;
    }

    public static void main(String[] args) {
        System.out.println(0xF);
        System.out.println(0x0F);
        System.out.println(0x7f);
        System.out.println(0x80);
        System.out.println(0x08);
    }
}
