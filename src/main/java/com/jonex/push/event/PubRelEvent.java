package com.jonex.push.event;

/**
 *  PubRel的事件类，只有Qos=2的时候才会有此事件
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public class PubRelEvent {
	String clientID;
	int packgeID;
	
	public PubRelEvent(String clientID, Integer pkgID){
		this.clientID = clientID;
		this.packgeID = pkgID;
	}

	public String getClientID() {
		return clientID;
	}

	public void setClientID(String clientID) {
		this.clientID = clientID;
	}

	public int getPackgeID() {
		return packgeID;
	}

	public void setPackgeID(int packgeID) {
		this.packgeID = packgeID;
	}
	
	
	
}
