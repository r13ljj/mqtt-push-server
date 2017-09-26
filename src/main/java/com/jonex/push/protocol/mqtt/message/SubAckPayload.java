package com.jonex.push.protocol.mqtt.message;

import java.util.List;

/**
 * MQTT协议SubAck消息类型的荷载
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class SubAckPayload{
	private List<Integer> grantedQosLevel;
	
	public SubAckPayload(List<Integer> grantedQosLevel) {
		this.grantedQosLevel = grantedQosLevel;
	}

	public List<Integer> getGrantedQosLevel() {
		return grantedQosLevel;
	}
	
}
