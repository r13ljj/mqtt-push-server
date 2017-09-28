package com.jonex.push.protocol.mqtt.message;

/**
 * MQTT协议Subscribe消息类型实现类，用于订阅topic，订阅了消息的客户端，可以接受对应topic的信息
 * 
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class SubscribeMessage extends Message {

	public SubscribeMessage(FixedHeader fixedHeader, PackageIdVariableHeader variableHeader,
			SubscribePayload payload) {
		super(fixedHeader, variableHeader, payload);
	}
	

	public PackageIdVariableHeader getVariableHeader() {
		return (PackageIdVariableHeader)super.getVariableHeader();
	}
	

	public SubscribePayload getPayload() {
		return (SubscribePayload)super.getPayload();
	}
	
}
