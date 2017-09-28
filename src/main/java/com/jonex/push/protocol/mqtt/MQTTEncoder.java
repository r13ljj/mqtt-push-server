package com.jonex.push.protocol.mqtt;

import com.jonex.push.protocol.mqtt.message.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:37
 */
public class MQTTEncoder extends MessageToByteEncoder<Message> {

    private static Logger LOG = LoggerFactory.getLogger(MQTTEncoder.class);

    private final static int MAX_LENGTH_LIMIT = 268435455;
    private final static byte[] EMPTY = new byte[0];
    private final int UTF8_FIX_LENGTH = 2;//UTF编码的byte，最开始必须为2字节的长度字段

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        ByteBufAllocator byteBufAllocator = ctx.alloc();
        ByteBuf encodedByteBuf;
        switch (msg.getFixedHeader().getMessageType()) {
            case CONNECT:
                encodedByteBuf = encodeConnectMessage(byteBufAllocator, (ConnectMessage)msg);
                break;
            case CONNACK:
                encodedByteBuf = encodeConnAckMessage(byteBufAllocator, (ConnAckMessage)msg);
            case PUBLISH:
                encodedByteBuf = encodePublishMessage(byteBufAllocator, (PublishMessage)msg);
            case SUBSCRIBE:
                encodedByteBuf = encodeSubscribeMessage(byteBufAllocator, (SubscribeMessage)msg);
                break;
            case UNSUBSCRIBE:
                encodedByteBuf = encodeUnSubcribeMessage(byteBufAllocator, (UnSubscribeMessage)msg);
                break;
            case SUBACK:
                encodedByteBuf = encodeSubAckMessage(byteBufAllocator, (SubAckMessage)msg);
                break;
            case UNSUBACK:
            case PUBACK:
            case PUBREC:
            case PUBREL:
            case PUBCOMP:
                encodedByteBuf = encodeMessageByteFixedHeaderAndPackageId(byteBufAllocator, msg);
                break;
            case PINGREQ:
            case PINGRESP:
            case DISCONNECT:
                encodedByteBuf = encodeMessageByteFixedHeader(byteBufAllocator, msg);
                break;
            default:
                throw new IllegalArgumentException(
                        "未知的MQTT协议类型："+msg.getFixedHeader().getMessageType().value());
        }
        out.writeBytes(encodedByteBuf);
    }

    private ByteBuf encodeConnectMessage(ByteBufAllocator byteBufAllocator, ConnectMessage connectMessage){
        //把消息每个字段从POJO中取出，并计算其大小，写入byteBuf
        int fixHeaderSize = 1;//固定头部有1字节+可变部分长度字节
        int variableHeaderSize = 10;//根据协议3.1.1，connect可变头固定大小为10
        int payloadSize = 0;

        FixedHeader fixedHeader = connectMessage.getFixedHeader();
        ConnectVariableHeader connectVariableHeader = connectMessage.getVariableHeader();
        ConnectPayload connectPayload = connectMessage.getPayload();

        //取出可变头部所有信息
        String mqttName = connectVariableHeader.getProtocolName();
        byte[] mqttNameBytes = encodeStringUTF8(mqttName);
        int mqttVersion = connectVariableHeader.getProtocolVersionNumber();
        int connectflags = connectVariableHeader.isCleanSession() ? 0x02 : 0;
        connectflags |= connectVariableHeader.isHasWill() ? 0x04 : 0;
        connectflags |= connectVariableHeader.getWillQoS() == null ? 0 : connectVariableHeader.getWillQoS().value() << 3;
        connectflags |= connectVariableHeader.isWillRetain() ? 0x20 : 0;
        connectflags |= connectVariableHeader.isHasPassword() ? 0x40 : 0;
        connectflags |= connectVariableHeader.isHasUsername() ? 0x80 : 0;
        int keepAlive = connectVariableHeader.getKeepAlive();

        //取出荷载信息并计算荷载的大小
        String clientId = connectPayload.getClientId();
        byte[] clientIdBytes = encodeStringUTF8(clientId);
        payloadSize += clientIdBytes.length;

        String willTopic = connectPayload.getWillTopic();
        byte[] willTopicBytes = (willTopic != null) ? encodeStringUTF8(willTopic) : EMPTY;
        String willMessage = connectPayload.getWillMessage();
        byte[] willMessageBytes = (willMessage != null) ? encodeStringUTF8(willMessage) : EMPTY;
        if (connectVariableHeader.isHasWill()) {
            payloadSize += UTF8_FIX_LENGTH;
            payloadSize += willTopicBytes.length;
            payloadSize += UTF8_FIX_LENGTH;
            payloadSize += willMessageBytes.length;
        }

        String userName = connectPayload.getUsername();
        byte[] userNameBytes = (userName != null) ? encodeStringUTF8(userName) : EMPTY;
        if (connectVariableHeader.isHasUsername()) {
            payloadSize += UTF8_FIX_LENGTH;
            payloadSize += userNameBytes.length;
        }

        String password = connectPayload.getPassword();
        byte[] passwordBytes = (password != null) ? encodeStringUTF8(password) : EMPTY;
        if (connectVariableHeader.isHasPassword()) {
            payloadSize += UTF8_FIX_LENGTH;
            payloadSize += passwordBytes.length;
        }

        //计算固定头部长度，长度为可变头部长度+荷载长度编码的长度
        fixHeaderSize = countVariableLengthInt(variableHeaderSize+payloadSize);

        //根据所有字段长度生成bytebuf
        ByteBuf byteBuf = byteBufAllocator.buffer(fixHeaderSize+variableHeaderSize+payloadSize);
        //写入bytebuf
        byteBuf.writeBytes(encodeFixHeader(fixedHeader));//写固定头部第一个字节
        byteBuf.writeBytes(encodeRemainLength(variableHeaderSize+payloadSize));//写固定头部第二个字节，剩余部分长度
        byteBuf.writeShort(mqttNameBytes.length);//写入协议名长度
        byteBuf.writeBytes(mqttNameBytes);//写入协议名
        byteBuf.writeByte(mqttVersion);//写入协议版本号
        byteBuf.writeByte(connectflags);//写入连接标志
        byteBuf.writeByte(keepAlive);//写入心跳包时长

        byteBuf.writeShort(clientIdBytes.length);//写入客户端ID长度
        byteBuf.writeBytes(clientIdBytes);//写入客户端ID

        if (connectVariableHeader.isHasWill()) {
            byteBuf.writeShort(willTopicBytes.length);//写入遗嘱主题长度
            byteBuf.writeBytes(willTopicBytes);//写入遗嘱主题
            byteBuf.writeShort(willMessageBytes.length);//写入遗嘱正文长度
            byteBuf.writeBytes(willMessageBytes);//写入遗嘱正文
        }
        if (connectVariableHeader.isHasUsername()) {
            byteBuf.writeShort(userNameBytes.length);
            byteBuf.writeBytes(userNameBytes);
        }
        if (connectVariableHeader.isHasPassword()) {
            byteBuf.writeShort(passwordBytes.length);
            byteBuf.writeBytes(passwordBytes);
        }

        return byteBuf;
    }

    private ByteBuf encodeConnAckMessage(ByteBufAllocator byteBufAllocator, ConnAckMessage connAckMessage){
        //由协议3.1.1 P28可知，ConnAck消息长度固定为4字节
        ByteBuf byteBuf = byteBufAllocator.buffer(4);
        byteBuf.writeBytes(encodeFixHeader(connAckMessage.getFixedHeader()));//写固定头部第一个字节
        byteBuf.writeByte(2);//写入可变头部长度，固定为2字节
        byteBuf.writeByte(connAckMessage.getVariableHeader().isSessionPresent()?0x01:0x00);//写入连接确认标志
        byteBuf.writeByte(connAckMessage.getVariableHeader().getStatus().value());//写入返回码
        return byteBuf;
    }

    private ByteBuf encodePublishMessage(ByteBufAllocator byteBufAllocator, PublishMessage message){
        int fixHeaderSize = 1;//固定头部有1字节+可变部分长度字节
        int variableHeaderSize = 0;
        int payloadSize = 0;//荷载大小

        FixedHeader fixedHeader = message.getFixedHeader();
        PublishVariableHeader variableHeader = message.getVariableHeader();
        ByteBuf payload = message.getPayload().duplicate();

        String topicName = variableHeader.getTopic();
        byte[] topicNameBytes = encodeStringUTF8(topicName);

        variableHeaderSize += UTF8_FIX_LENGTH;
        variableHeaderSize += topicNameBytes.length;
        variableHeaderSize += fixedHeader.getQos().value()>0?2:0;//根据qos判断packageID的长度是否需要加上
        payloadSize = payload.readableBytes();
        fixHeaderSize += countVariableLengthInt(variableHeaderSize+payloadSize);

        //生成bytebuf
        ByteBuf byteBuf = byteBufAllocator.buffer(fixHeaderSize + variableHeaderSize + payloadSize);
        //写入byteBuf
        byteBuf.writeBytes(encodeFixHeader(fixedHeader));//写固定头部第一个字节
        byteBuf.writeBytes(encodeRemainLength(variableHeaderSize + payloadSize));//写固定头部第二个字节，剩余部分长度
        byteBuf.writeShort(topicNameBytes.length);
        byteBuf.writeBytes(topicNameBytes);
        if (fixedHeader.getQos().value()>0) {
            byteBuf.writeShort(variableHeader.getPackageID());
        }
        byteBuf.writeBytes(payload);//写入荷载

        return byteBuf;
    }

    private ByteBuf encodeSubscribeMessage(ByteBufAllocator bufAllocator,
                                           SubscribeMessage message){
        int fixHeaderSize = 1;//固定头部有1字节+可变部分长度字节
        int variableHeaderSize = 2;//协议P37页，订阅类型的可变头部长度都为2
        int payloadSize = 0;//荷载大小

        FixedHeader fixedHeader = message.getFixedHeader();
        PackageIdVariableHeader variableHeader = message.getVariableHeader();
        SubscribePayload payload = message.getPayload();

        //遍历订阅消息组，计算荷载长度
        for (TopicSubscribe topic : payload.getTopicSubscribes()) {
            String topicName = topic.getTopicFilter();
            byte[] topicNameBytes = encodeStringUTF8(topicName);
            payloadSize += UTF8_FIX_LENGTH;
            payloadSize += topicNameBytes.length;
            payloadSize += 1;//添加qos的长度，qos长度只能为1
        }

        fixHeaderSize += countVariableLengthInt(variableHeaderSize+payloadSize);

        //生成bytebuf
        ByteBuf byteBuf = bufAllocator.buffer(fixHeaderSize + variableHeaderSize + payloadSize);
        //写入byteBuf
        byteBuf.writeBytes(encodeFixHeader(fixedHeader));//写固定头部第一个字节
        byteBuf.writeBytes(encodeRemainLength(variableHeaderSize + payloadSize));//写固定头部第二个字节，剩余部分长度
        byteBuf.writeShort(variableHeader.getPackageID());//写入可变头部中的包ID
        //写入荷载
        for (TopicSubscribe topic : payload.getTopicSubscribes()) {
            String topicName = topic.getTopicFilter();
            byte[] topicNameBytes = encodeStringUTF8(topicName);
            byteBuf.writeShort(topicNameBytes.length);
            byteBuf.writeBytes(topicNameBytes);
            byteBuf.writeByte(topic.getQos().value());
        }

        return byteBuf;
    }

    private ByteBuf encodeUnSubcribeMessage(ByteBufAllocator bufAllocator,
                                            UnSubscribeMessage message){
        int fixHeaderSize = 1;//固定头部有1字节+可变部分长度字节
        int variableHeaderSize = 2;//协议P42页，取消订阅类型的可变头部长度固定为2
        int payloadSize = 0;//荷载大小

        FixedHeader fixedHeader = message.getFixedHeader();
        PackageIdVariableHeader variableHeader = message.getVariableHeader();
        UnSubscribePayload payload = message.getPayload();

        for (String topic : payload.getTopics()) {
            byte[] topicBytes = encodeStringUTF8(topic);
            payloadSize += UTF8_FIX_LENGTH;
            payloadSize += topicBytes.length;
        }

        fixHeaderSize += countVariableLengthInt(variableHeaderSize+payloadSize);

        //生成bytebuf
        ByteBuf byteBuf = bufAllocator.buffer(fixHeaderSize + variableHeaderSize + payloadSize);
        //写入byteBuf
        byteBuf.writeBytes(encodeFixHeader(fixedHeader));//写固定头部第一个字节
        byteBuf.writeBytes(encodeRemainLength(variableHeaderSize + payloadSize));//写固定头部第二个字节，剩余部分长度
        byteBuf.writeShort(variableHeader.getPackageID());//写入可变头部中的包ID
        //写入荷载
        for (String topic : payload.getTopics()) {
            byte[] topicBytes = encodeStringUTF8(topic);
            byteBuf.writeShort(topicBytes.length);
            byteBuf.writeBytes(topicBytes);
        }

        return byteBuf;
    }

    private ByteBuf encodeSubAckMessage(ByteBufAllocator bufAllocator,
                                        SubAckMessage message){
        int fixHeaderSize = 1;//固定头部有1字节+可变部分长度字节
        int variableHeaderSize = 2;//协议P42页，取消订阅类型的可变头部长度固定为2
        int payloadSize = 0;//荷载大小

        FixedHeader fixedHeader = message.getFixedHeader();
        PackageIdVariableHeader variableHeader = message.getVariableHeader();
        SubAckPayload payload = message.getPayload();

        List<Integer> grantedQosLevels = payload.getGrantedQosLevel();
        payloadSize += grantedQosLevels.size();

        fixHeaderSize += countVariableLengthInt(variableHeaderSize+payloadSize);

        //生成bytebuf
        ByteBuf byteBuf = bufAllocator.buffer(fixHeaderSize + variableHeaderSize + payloadSize);
        //写入byteBuf
        byteBuf.writeBytes(encodeFixHeader(fixedHeader));//写固定头部第一个字节
        byteBuf.writeBytes(encodeRemainLength(variableHeaderSize + payloadSize));//写固定头部第二个字节，剩余部分长度
        byteBuf.writeShort(variableHeader.getPackageID());//写入可变头部中的包ID
        for (Integer qos : grantedQosLevels) {
            byteBuf.writeByte(qos);
        }

        return byteBuf;
    }

    private ByteBuf encodeMessageByteFixedHeaderAndPackageId(ByteBufAllocator bufAllocator,
                                                             Message message){

        int fixHeaderSize = 1;//固定头部有1字节+可变部分长度字节
        int variableHeaderSize = 2;//只包含包ID的可变头部，长度固定为2

        FixedHeader fixedHeader = message.getFixedHeader();
        PackageIdVariableHeader variableHeader = (PackageIdVariableHeader)message.getVariableHeader();

        fixHeaderSize += countVariableLengthInt(variableHeaderSize);

        //生成bytebuf
        ByteBuf byteBuf = bufAllocator.buffer(fixHeaderSize + variableHeaderSize);
        //写入byteBuf
        byteBuf.writeBytes(encodeFixHeader(fixedHeader));//写固定头部第一个字节
        byteBuf.writeBytes(encodeRemainLength(variableHeaderSize));//写固定头部第二个字节，剩余部分长度
        byteBuf.writeShort(variableHeader.getPackageID());//写入可变头部中的包ID

        return byteBuf;
    }

    private ByteBuf encodeMessageByteFixedHeader(ByteBufAllocator bufAllocator, Message message){

        int fixHeaderSize = 2;//固定头部加上一个字节的剩余长度（剩余长度为0）
        FixedHeader fixedHeader = message.getFixedHeader();

        ByteBuf byteBuf = bufAllocator.buffer(fixHeaderSize);
        byteBuf.writeBytes(encodeFixHeader(fixedHeader));
        byteBuf.writeByte(0);//写入剩余长度，没有可变头部和荷载，所以剩余长度为0

        return byteBuf;
    }


    /**
     * 把消息长度信息编码成字节
     *
     * @param length
     * @return byte[]
     */
    private ByteBuf encodeRemainLength(int length){
        if (length > MAX_LENGTH_LIMIT || length < 0) {
            throw new CorruptedFrameException(
                    "消息长度不能超过‘消息最大长度’:"+MAX_LENGTH_LIMIT+",当前长度："+length);
        }
        //剩余长度字段最多可编码4字节
        ByteBuf encoded = Unpooled.buffer(4);
        byte digit;
        do{
            digit = (byte)(length%128);
            length = length / 128;
            if (length > 0) {
                digit = (byte) (digit | 0x80);
            }
            encoded.writeByte(digit);
        }while (length > 0);
        return encoded;
    }


    /**
     * 编码固定头部第一个字节
     *
     * @param fixedHeader
     * @return byte[]
     */
    private byte[] encodeFixHeader(FixedHeader fixedHeader){
        byte b = 0;
        b = (byte)(fixedHeader.getMessageType().value() << 4);
        b |= fixedHeader.isDup() ? 0x08 : 0x00;
        b |= fixedHeader.getQos().value() << 1;
        b |= fixedHeader.isRetain() ? 0x01 : 0x00;
        byte[] byteArray = new byte[]{b};
        return byteArray;
    }


    /**
     * 计算固定头部中长度编码占用的字节</br>
     * 协议3.1.1 P16对长度有说明，长度/128即可得到需要使用的字节数，一直除到0
     *
     * @param length
     * @return int
     */
    private int countVariableLengthInt(int length){
        int count = 0;
        do{
            length /= 128;
            count ++;
        }while (length > 0);
        return count;
    }

    /**
     * 将String类型编码为byte[]
     *
     * @param str
     * @return byte[]
     */
    private byte[] encodeStringUTF8(String str){
        return str.getBytes(CharsetUtil.UTF_8);
    }

    public static void main(String[] args) {
        System.out.println(Integer.MAX_VALUE);
        System.out.println(0x02);
        System.out.println(0x04);
        System.out.println(0x08);
        System.out.println("-----------------");
        int connectflags = 0x02;
        connectflags |= true ? 0x04 : 0;
        System.out.println(connectflags);
        System.out.println("-----------------");
        System.out.println(1 << 4);
        System.out.println(2 << 4);
        System.out.println("-----------------");
        System.out.println(0x8);
        System.out.println(0x08);
    }
}
