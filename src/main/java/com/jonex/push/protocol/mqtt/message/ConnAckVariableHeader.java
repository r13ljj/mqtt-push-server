package com.jonex.push.protocol.mqtt.message;

/**
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */
public class ConnAckVariableHeader{
	private ConnAckMessage.ConnectionStatus status;//返回给客户端的状态码
	private Boolean sessionPresent;//sessionPresent是告知客户端服务器是否存储了session的位
	
	public ConnAckVariableHeader(ConnAckMessage.ConnectionStatus status, Boolean sessionPresent) {
		this.status = status;
		this.sessionPresent = sessionPresent;
	}
	public ConnAckMessage.ConnectionStatus getStatus() {
		return status;
	}
	public void setStatus(ConnAckMessage.ConnectionStatus status) {
		this.status = status;
	}
	public Boolean isSessionPresent() {
		return sessionPresent;
	}
	public void setSessionPresent(Boolean sessionPresent) {
		this.sessionPresent = sessionPresent;
	}
}
