package com.jonex.push.protocol.mqtt.message;

/**
 * Subscribe荷载的封装，一个topic和一个qos是一组荷载
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class TopicSubscribe{
	private String topicFilter;
	private Qos qos;
	
	public TopicSubscribe(String topicFilter, Qos qos) {
		super();
		this.topicFilter = topicFilter;
		this.qos = qos;
	}
	
	public String getTopicFilter() {
		return topicFilter;
	}
	public void setTopicFilter(String topicFilter) {
		this.topicFilter = topicFilter;
	}
	public Qos getQos() {
		return qos;
	}
	public void setQos(Qos qos) {
		this.qos = qos;
	}
}