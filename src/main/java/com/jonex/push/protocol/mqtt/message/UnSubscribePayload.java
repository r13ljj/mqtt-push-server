package com.jonex.push.protocol.mqtt.message;

import java.util.List;

/**
 * MQTT协议Connect消息类型的荷载
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class UnSubscribePayload{
	
	private List<String> topics;
	
	public UnSubscribePayload(List<String> topics) {
		super();
		this.topics = topics;
	}

	public List<String> getTopics() {
		return topics;
	}

}