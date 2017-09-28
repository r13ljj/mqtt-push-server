package com.jonex.push.protocol.mqtt.message;

/**
 * MQTT协议Connect消息类型实现类，客户端请求服务器连接的消息类型
 * 
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class ConnectMessage extends Message {
	
	public ConnectMessage(FixedHeader fixedHeader, ConnectVariableHeader variableHeader,
			ConnectPayload payload) {
		super(fixedHeader, variableHeader, payload);
	}
	

	public ConnectVariableHeader getVariableHeader() {
		return (ConnectVariableHeader)super.getVariableHeader();
	}
	

	public ConnectPayload getPayload() {
		return (ConnectPayload)super.getPayload();
	}

}
