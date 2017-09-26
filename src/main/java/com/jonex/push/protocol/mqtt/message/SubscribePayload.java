package com.jonex.push.protocol.mqtt.message;

import java.util.List;

/**
 * MQTT协议Subscribe消息类型的荷载
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class SubscribePayload{
	private List<TopicSubscribe> topicSubscribes;

	public SubscribePayload(List<TopicSubscribe> topicSubscribes) {
		super();
		this.topicSubscribes = topicSubscribes;
	}

	public List<TopicSubscribe> getTopicSubscribes() {
		return topicSubscribes;
	}

}

