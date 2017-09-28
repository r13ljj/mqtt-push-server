package com.jonex.push.store;

import com.jonex.push.subscribe.Subscription;

/**
 *  会话存储类
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public interface ISessionStore {

	/**
	 * 查看是否已储存了该客户端ID，如果存储了则返回true
	 * @param clientID
	 * @return boolean
	 */
	boolean searchSubscriptions(String clientID);
	
	/**
	 * 清理某个ID所有订阅信息
	 * @param clientID
	 */
	void wipeSubscriptions(String clientID);
	

	/**
	 * 添加某个订阅消息到存储
	 * @param newSubscription
	 * @param clientID

	 */
	void addNewSubscription(Subscription newSubscription, String clientID);
	
	/**
	 * 从会话的持久化存储中移除某个订阅主题中的某个client
	 * @param topic
	 * @param clientID

	 */
	void removeSubscription(String topic, String clientID);
	
}
