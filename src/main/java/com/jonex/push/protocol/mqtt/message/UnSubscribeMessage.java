package com.jonex.push.protocol.mqtt.message;

/**
 * MQTT协议UnSubscribe消息类型实现类，用于取消订阅topic
 * 
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class UnSubscribeMessage extends Message {

	public UnSubscribeMessage(FixedHeader fixedHeader, PackageIdVariableHeader variableHeader,
			UnSubscribePayload payload) {
		super(fixedHeader, variableHeader, payload);
	}
	
	@Override
	public PackageIdVariableHeader getVariableHeader() {
		return (PackageIdVariableHeader)super.getVariableHeader();
	}
	
	@Override
	public UnSubscribePayload getPayload() {
		return (UnSubscribePayload)super.getPayload();
	}
	
}
