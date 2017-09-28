package com.jonex.push.store.impl;

import com.jonex.push.event.PubRelEvent;
import com.jonex.push.event.PublishEvent;
import com.jonex.push.protocol.mqtt.message.Qos;
import com.jonex.push.store.IMessagesStore;
import com.jonex.push.store.ISessionStore;
import com.jonex.push.subscribe.SubscribeStore;
import com.jonex.push.subscribe.Subscription;
import com.jonex.push.util.MqttProperties;
import io.netty.buffer.ByteBuf;
import org.apache.log4j.Logger;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 *  对数据进行保存，视情况决定是临时保存还是持久化保存
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public class MapDBPersistentStore implements IMessagesStore, ISessionStore {

	private final static Logger Log = Logger.getLogger(MapDBPersistentStore.class);
	
	//为Session保存的的可能需要重发的消息
	private ConcurrentMap<String, List<PublishEvent>> persistentOfflineMessage;
	//为Qos1和Qos2临时保存的消息
	private ConcurrentMap<String, PublishEvent> persistentQosTempMessage;
	//为Qos2临时保存的PubRel消息
	private ConcurrentMap<String, PubRelEvent> persistentPubRelTempMessage;
	//持久化存储session和与之对应的subscription Set
	private ConcurrentMap<String, Set<Subscription>> persistentSubscriptionStore;
	//持久化的Retain
	private ConcurrentMap<String, StoredMessage> retainedStore;
	//保存publish包ID
	private ConcurrentMap<String, Integer> publishPackgeIDStore;
	//保存pubRec包ID
	private ConcurrentMap<String, Integer> pubRecPackgeIDStore;
	
	private DB m_db;
	

	public void initStore() {
		 String STORAGE_FILE_PATH =  System.getProperty("user.dir") + File.separator + MqttProperties.getProperty("path");
		 Log.info("存储文件的初始化位置"+STORAGE_FILE_PATH);
	     File tmpFile;
	     try {
	    	 tmpFile = new File(STORAGE_FILE_PATH);
	         tmpFile.createNewFile();
	         m_db = DBMaker.newFileDB(tmpFile).make();
		     persistentOfflineMessage = m_db.getHashMap("offline");
		     persistentQosTempMessage = m_db.getHashMap("publishTemp");
		     persistentPubRelTempMessage = m_db.getHashMap("pubRelTemp");
		     persistentSubscriptionStore = m_db.getHashMap("subscriptions");
		     retainedStore = m_db.getHashMap("retained");
		     publishPackgeIDStore = m_db.getHashMap("publishPID");
		     pubRecPackgeIDStore = m_db.getHashMap("pubRecPID");
	     } catch (IOException ex) {
	         Log.error(null, ex);
	     }     
	 }
	

	public boolean searchSubscriptions(String clientID) {
		return persistentSubscriptionStore.containsKey(clientID);
	}


	public void wipeSubscriptions(String clientID) {
		persistentSubscriptionStore.remove(clientID);
		m_db.commit();
	}


	public void addNewSubscription(Subscription newSubscription, String clientID) {
		Log.info("添加新订阅，订阅:" + newSubscription + ",客户端ID:" + clientID );
		 if (!persistentSubscriptionStore.containsKey(clientID)) {
	            Log.info("客户端ID{" + clientID + "}不存在订阅集 , 为它创建订阅集");
	            persistentSubscriptionStore.put(clientID, new HashSet<Subscription>());
	     }
		 
		 Set<Subscription> subs = persistentSubscriptionStore.get(clientID);
		 if (!subs.contains(newSubscription)) {
	            Log.info("更新客户端" + clientID + "的订阅集");
	            Subscription existingSubscription = null;
	            //遍历订阅集里所有的订阅，查看是否有相同topic的订阅，有的话，移除之前的订阅，添加新的
	            for (Subscription scanSub : subs) {
	                if (newSubscription.getTopicFilter().equals(scanSub.getTopicFilter())) {
	                    existingSubscription = scanSub;
	                    break;
	                }
	            }
	            if (existingSubscription != null) {
	                subs.remove(existingSubscription);
	            }
	            subs.add(newSubscription);
	            persistentSubscriptionStore.put(clientID, subs);
	            Log.debug("客户端" + clientID + "的订阅集现在是这样的" + subs);
	      }
		  m_db.commit();
	}


	public void removeSubscription(String topic, String clientID) {
		Log.info("删除客户端" + clientID + "的" + topic + "订阅");
		if (!persistentSubscriptionStore.containsKey(clientID)) {
            Log.debug("没客户端ID" + clientID + " , 无法删除");
            return;
        }
		Set<Subscription> subs = persistentSubscriptionStore.get(clientID);
		Subscription existingSubscription = null;
		for (Subscription subscription : subs) {
			String topicfilter = subscription.getTopicFilter();
			if (topicfilter.equals(topic)) {
				existingSubscription = subscription;
			}
		}
		if (existingSubscription != null) {
            subs.remove(existingSubscription);
        }
		m_db.commit();
	}


	public List<PublishEvent> listMessagesInSession(String clientID) {
		List<PublishEvent> allEvents = new ArrayList<PublishEvent>();
		List<PublishEvent> storeEvents = persistentOfflineMessage.get(clientID);
		//如果该client无离线消息，则把storeEvents设置为空集合
		if (storeEvents == null) {
			storeEvents = Collections.<PublishEvent>emptyList();
		}
		for (PublishEvent event : storeEvents) {
			allEvents.add(event);
		}
		return allEvents;
	}


	public void removeMessageInSessionForPublish(String clientID,
			Integer packgeID) {
		List<PublishEvent> events = persistentOfflineMessage.get(clientID);
		if (events == null) {
			return;
		}
		PublishEvent toRemoveEvt = null;
		for (PublishEvent evt : events) {
	            if (evt.getPackgeID()== packgeID) {
	                toRemoveEvt = evt;
	            }
	     }
		events.remove(toRemoveEvt);
		persistentOfflineMessage.put(clientID, events);
		m_db.commit();
	}


	public void storeMessageToSessionForPublish(PublishEvent pubEvent) {
		 	List<PublishEvent> storedEvents;
	        String clientID = pubEvent.getClientID();
	        if (!persistentOfflineMessage.containsKey(clientID)) {
	            storedEvents = new ArrayList<PublishEvent>();
	        } else {
	            storedEvents = persistentOfflineMessage.get(clientID);
	        }
	        storedEvents.add(pubEvent);
	        persistentOfflineMessage.put(clientID, storedEvents);
	        m_db.commit();
	}


	public void storeQosPublishMessage(String publishKey, PublishEvent pubEvent) {
		persistentQosTempMessage.put(publishKey, pubEvent);
		m_db.commit();
	}


	public void removeQosPublishMessage(String publishKey) {
		persistentQosTempMessage.remove(publishKey);
		m_db.commit();
	}
	

	public PublishEvent searchQosPublishMessage(String publishKey) {
		return persistentQosTempMessage.get(publishKey);
	}
	

	public void storePubRelMessage(String pubRelKey, PubRelEvent pubRelEvent) {
		persistentPubRelTempMessage.put(pubRelKey, pubRelEvent);
		m_db.commit();
	}


	public void removePubRelMessage(String pubRelKey) {
		persistentPubRelTempMessage.remove(pubRelKey);
		m_db.commit();
	}


	public PubRelEvent searchPubRelMessage(String pubRelKey) {
		return persistentPubRelTempMessage.get(pubRelKey);
	}


	public void storeRetained(String topic, ByteBuf message, Qos qos) {
		//将ByteBuf转变为byte[]
		byte[] messageBytes = new byte[message.readableBytes()];
		message.getBytes(message.readerIndex(), messageBytes);
		if (messageBytes.length <= 0) {
			retainedStore.remove(topic);
		} else {
			StoredMessage storedMessage = new StoredMessage(messageBytes, qos, topic);
			retainedStore.put(topic, storedMessage);
		}
		m_db.commit();
	}


	public void cleanRetained(String topic) {
		retainedStore.remove(topic);
		m_db.commit();
	}


	public Collection<StoredMessage> searchRetained(String topic) {
		List<StoredMessage> results = new ArrayList<StoredMessage>();
		 for (Map.Entry<String, StoredMessage> entry : retainedStore.entrySet()) {
	            StoredMessage storedMsg = entry.getValue();
	            if (SubscribeStore.matchTopics(entry.getKey(), topic)) {
	                results.add(storedMsg);
	            }
	      }
		return results;
	}


	public void storePublicPackgeID(String clientID, Integer packgeID) {
		publishPackgeIDStore.put(clientID, packgeID);
		m_db.commit();
	}


	public void removePublicPackgeID(String clientID) {
		publishPackgeIDStore.remove(clientID);
		m_db.commit();
	}


	public void storePubRecPackgeID(String clientID, Integer packgeID) {
		pubRecPackgeIDStore.put(clientID, packgeID);
		m_db.commit();
	}
	

	public void removePubRecPackgeID(String clientID) {
		pubRecPackgeIDStore.remove(clientID);
		m_db.commit();
	}

}
