package com.jonex.push.protocol.mqtt.message;

/**
 * MQTT协议SubAck消息类型实现类，对Subscribe包的确认
 * 
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class SubAckMessage extends Message {

	public SubAckMessage(FixedHeader fixedHeader, PackageIdVariableHeader variableHeader,
			SubAckPayload payload) {
		super(fixedHeader, variableHeader, payload);
	}
	
	@Override
	public PackageIdVariableHeader getVariableHeader() {
		return (PackageIdVariableHeader)super.getVariableHeader();
	}
	
	@Override
	public SubAckPayload getPayload() {
		return (SubAckPayload)super.getPayload();
	}
}
