package com.jonex.push.protocol.mqtt.message;

/**
 * MQTT协议Publish消息类型的可变头部
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class PublishVariableHeader{
	private String topic;
	private int packageID;
	
	public PublishVariableHeader(String topic) {
		this.topic = topic;
	}
	
	public PublishVariableHeader(String topic, int packageID) {
		this.topic = topic;
		this.packageID = packageID;
	}
	
	public String getTopic() {
		return topic;
	}
	public void setTopic(String topic) {
		this.topic = topic;
	}
	public int getPackageID() {
		return packageID;
	}
	public void setPackageID(int packageID) {
		this.packageID = packageID;
	}
	
}