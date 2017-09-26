package com.jonex.push.protocol.mqtt.message;

/**
 * Mqtt协议的固定头部，有一个字节，由四个字段组成
 * ----------------------------------------------------------------------
 * bit  |   7   |   6   |   5   |   4   |   3   |   2   |   1   |   0   |
 * ----------------------------------------------------------------------
 * byte1|       MQTT控制报文的类型        |    用于指定控制报文类型的标志位  |
 * ----------------------------------------------------------------------
 * byte2|                       剩余长度
 * ----------------------------------------------------------------------
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:41
 */
public class FixedHeader {

    private final MessageType messageType;
    private boolean dup;    //MQTT协议头第5bit，代表打开标志，表示是否第一次发送
    private Qos Qos;    //MQTT协议头前6,7bit，代表服务质量
    private boolean retain; //MQTT协议头第8bit，代表是否保持
    private int messageLength;  //第二个字节


    public FixedHeader(MessageType messageType, boolean dup, Qos Qos, boolean retain) {
        this.messageType = messageType;
        this.dup = dup;
        this.Qos = Qos;
        this.retain = retain;
    }

    public FixedHeader(MessageType messageType, boolean dup, Qos Qos, boolean retain, int messageLength) {
        this.messageType = messageType;
        this.dup = dup;
        this.Qos = Qos;
        this.retain = retain;
        this.messageLength = messageLength;
    }


    public static FixedHeader getConnectFixedHeader(){
        return new FixedHeader(MessageType.CONNECT, false, com.jonex.push.protocol.mqtt.message.Qos.AT_MOST_ONCE, false);
    }

    public static FixedHeader getConnAckFixedHeader(){
        return new FixedHeader(MessageType.CONNACK, false, com.jonex.push.protocol.mqtt.message.Qos.AT_MOST_ONCE, false);
    }

    public static FixedHeader getPublishFixedHeader(
            boolean dup,
            Qos Qos,
            boolean retain){
        return new FixedHeader(MessageType.PUBLISH, dup, Qos, retain);
    }

    public static FixedHeader getPubAckFixedHeader(){
        return new FixedHeader(MessageType.PUBACK, false, com.jonex.push.protocol.mqtt.message.Qos.AT_MOST_ONCE, false);
    }

    public static FixedHeader getPubRecFixedHeader(){
        return new FixedHeader(MessageType.PUBREC, false, com.jonex.push.protocol.mqtt.message.Qos.AT_MOST_ONCE, false);
    }

    public static FixedHeader getPubRelFixedHeader(){
        return new FixedHeader(MessageType.PUBREL, false, com.jonex.push.protocol.mqtt.message.Qos.AT_LEAST_ONCE, false);
    }

    public static FixedHeader getPubCompFixedHeader(){
        return new FixedHeader(MessageType.PUBCOMP, false, com.jonex.push.protocol.mqtt.message.Qos.AT_MOST_ONCE, false);
    }

    public static FixedHeader getSubscribeFixedHeader(){
        return new FixedHeader(MessageType.SUBSCRIBE, false, com.jonex.push.protocol.mqtt.message.Qos.AT_LEAST_ONCE, false);
    }

    public static FixedHeader getSubAckFixedHeader(){
        return new FixedHeader(MessageType.SUBACK, false, com.jonex.push.protocol.mqtt.message.Qos.AT_MOST_ONCE, false);
    }

    public static FixedHeader getUnSubscribeFixedHeader(){
        return new FixedHeader(MessageType.UNSUBSCRIBE, false, com.jonex.push.protocol.mqtt.message.Qos.AT_LEAST_ONCE, false);
    }

    public static FixedHeader getUnSubAckFixedHeader(){
        return new FixedHeader(MessageType.UNSUBACK, false, com.jonex.push.protocol.mqtt.message.Qos.AT_MOST_ONCE, false);
    }

    public static FixedHeader getPingRespFixedHeader(){
        return new FixedHeader(MessageType.PINGRESP, false, com.jonex.push.protocol.mqtt.message.Qos.AT_MOST_ONCE, false);
    }

    public static FixedHeader getDisconnectFixedHeader(){
        return new FixedHeader(MessageType.DISCONNECT, false, com.jonex.push.protocol.mqtt.message.Qos.AT_MOST_ONCE, false);
    }


    /* getter and setter */
    public MessageType getMessageType() {
        return messageType;
    }

    public boolean isDup() {
        return dup;
    }

    public void setDup(boolean dup) {
        this.dup = dup;
    }

    public com.jonex.push.protocol.mqtt.message.Qos getQos() {
        return Qos;
    }

    public void setQos(com.jonex.push.protocol.mqtt.message.Qos qos) {
        Qos = qos;
    }

    public boolean isRetain() {
        return retain;
    }

    public void setRetain(boolean retain) {
        this.retain = retain;
    }

    public int getMessageLength() {
        return messageLength;
    }

    public void setMessageLength(int messageLength) {
        this.messageLength = messageLength;
    }

    @Override
    public String toString() {
        return "FixedHeader{" +
                "messageType=" + messageType +
                ", dup=" + dup +
                ", Qos=" + Qos +
                ", retain=" + retain +
                ", messageLength=" + messageLength +
                '}';
    }
}
