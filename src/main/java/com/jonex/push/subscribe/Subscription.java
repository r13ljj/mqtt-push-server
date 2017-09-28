package com.jonex.push.subscribe;

import com.jonex.push.protocol.mqtt.message.Qos;

import java.io.Serializable;

/**
 *  订阅的树节点，保存订阅的每个节点的信息
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public class Subscription implements Serializable{

	private static final long serialVersionUID = 6159635655653102856L;
	Qos requestedQos; //max QoS acceptable
    String topicFilter;
    String clientID;
    boolean cleanSession;
    boolean active = true;
    
    public Subscription(String clientID, String topicFilter, Qos requestedQos, boolean cleanSession) {
    	this.clientID = clientID;
        this.requestedQos = requestedQos;
        this.topicFilter = topicFilter;
        this.cleanSession = cleanSession;
    }
    
    public Qos getRequestedQos() {
		return requestedQos;
	}

	public void setRequestedQos(Qos requestedQos) {
		this.requestedQos = requestedQos;
	}

	public String getTopicFilter() {
		return topicFilter;
	}

	public void setTopicFilter(String topicFilter) {
		this.topicFilter = topicFilter;
	}
 
	public boolean isCleanSession() {
		return cleanSession;
	}

	public void setCleanSession(boolean cleanSession) {
		this.cleanSession = cleanSession;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public void setClientID(String clientID) {
		this.clientID = clientID;
	}

	public String getClientID() {
		return clientID;
	}
 

    public String toString() {
        return String.format("[filter:%s, cliID: %s, qos: %s, active: %s]", this.topicFilter, this.clientID, this.requestedQos, this.active);
    }
}
