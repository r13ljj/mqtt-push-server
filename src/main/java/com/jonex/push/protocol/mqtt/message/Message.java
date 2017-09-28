package com.jonex.push.protocol.mqtt.message;

/**
 * 基类Message拆分为固定头部fixHeader，可变头部variableHeader，负载payload三部分
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class Message {

    private final FixedHeader fixedHeader;
    private final Object variableHeader;
    private final Object payload;

    public Message(FixedHeader fixedHeader, Object variableHeader, Object payload) {
        this.fixedHeader = fixedHeader;
        this.variableHeader = variableHeader;
        this.payload = payload;
    }


    /**
     * 初始化message，针对没有可变头部和荷载的协议类型
     *
     * @param fixedHeader
     */
    public Message(FixedHeader fixedHeader) {
        this(fixedHeader, null);
    }

    /**
     * 初始化message，针对没有荷载的协议类型
     *
     * @param fixedHeader
     * @param variableHeader
     */
    public Message(FixedHeader fixedHeader, Object variableHeader) {
        this(fixedHeader, variableHeader, null);
    }

    public FixedHeader getFixedHeader() {
        return fixedHeader;
    }

    public Object getVariableHeader() {
        return variableHeader;
    }

    public Object getPayload() {
        return payload;
    }


    public String toString() {
        return "Message{" +
                "fixedHeader=" + fixedHeader +
                ", variableHeader=" + variableHeader +
                ", payload=" + payload +
                '}';
    }
}
