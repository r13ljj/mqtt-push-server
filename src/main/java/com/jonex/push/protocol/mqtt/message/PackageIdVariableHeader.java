package com.jonex.push.protocol.mqtt.message;

/**
 * MQTT协议中，有部分消息类型的可变头部只含有包ID，把这部分抽取出来，单独成为一个可变头部
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/26 11:38
 */	
public class PackageIdVariableHeader {
	
	private int packageID;

	public PackageIdVariableHeader(int packageID) {
		if (packageID < 1 || packageID > 65535) {
			throw new IllegalArgumentException("消息ID:" + packageID + "必须在1~65535范围内");
		}
		this.packageID = packageID;
	}

	public int getPackageID() {
		return packageID;
	}

	public void setPackageID(int packageID) {
		this.packageID = packageID;
	}
	
}
