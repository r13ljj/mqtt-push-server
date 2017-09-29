package com.jonex.push.protocol;


import com.jonex.push.event.PubRelEvent;
import com.jonex.push.event.PublishEvent;
import com.jonex.push.job.QuartzManager;
import com.jonex.push.job.RePubRelJob;
import com.jonex.push.job.RePublishJob;
import com.jonex.push.netty.NettyAttrManager;
import com.jonex.push.protocol.mqtt.MQTTMessageFactory;
import com.jonex.push.protocol.mqtt.message.*;
import com.jonex.push.server.ConnectionDescriptor;
import com.jonex.push.store.IAuthenticator;
import com.jonex.push.store.IMessagesStore;
import com.jonex.push.store.ISessionStore;
import com.jonex.push.store.impl.IdentityAuthenticator;
import com.jonex.push.store.impl.MapDBPersistentStore;
import com.jonex.push.subscribe.SubscribeStore;
import com.jonex.push.subscribe.Subscription;
import com.jonex.push.util.StringTool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 协议所有的业务处理都在此类，注释中所指协议为MQTT3.3.1协议英文版
 *
 * @Author jonex [r13ljj@gmail.com]
 * @Date 2017/9/27 14:32
 */
public class ProtocolProcess{

    private final static Logger LOG = LoggerFactory.getLogger(ProtocolProcess.class);

    // 客户端链接映射表
    private ConcurrentMap<Object, ConnectionDescriptor> clients = new ConcurrentHashMap<Object, ConnectionDescriptor>();
    //存储遗嘱信息，通过ID映射遗嘱信息
    private ConcurrentMap<String, WillMessage> willStore = new ConcurrentHashMap<String, WillMessage>();

    private IAuthenticator authenticator;
    private IMessagesStore messagesStore;
    private ISessionStore sessionStore;
    private SubscribeStore subscribeStore;

    private ProtocolProcess(){
        MapDBPersistentStore storge = new MapDBPersistentStore();
        this.authenticator = new IdentityAuthenticator();
        this.messagesStore = storge;
        this.messagesStore.initStore();//初始化存储
        this.sessionStore = storge;
        this.subscribeStore = new SubscribeStore();
    }

    private static final class ProtocolProcessHolder{
        private final static ProtocolProcess instance = new ProtocolProcess();
    }

    public static ProtocolProcess getInstance(){
        return ProtocolProcessHolder.instance;
    }

    /**
     * 处理协议的CONNECT消息类型
     *
     * @param client
     * @param connectMessage
     */
    public void processConnect(Channel client, ConnectMessage connectMessage){
        //首先查看保留位是否为0，不为0则断开连接,协议P24
        if (!connectMessage.getVariableHeader().isReservedIsZero()) {
            client.close();
            return;
        }
        //处理protocol name和protocol version, 如果返回码!=0，sessionPresent必为0，协议P24,P32
        if (!"MQTT".equals(connectMessage.getVariableHeader().getProtocolName())
                || 4 != connectMessage.getVariableHeader().getProtocolVersionNumber()) {
            ConnAckMessage connAckMessage = (ConnAckMessage)MQTTMessageFactory.newMessage(connectMessage.getFixedHeader(),
                    new ConnAckVariableHeader(ConnAckMessage.ConnectionStatus.UNACCEPTABLE_PROTOCOL_VERSION, false),
                    null);
            client.writeAndFlush(connAckMessage);
            //版本或协议名不匹配，则断开该客户端连接
            client.close();
            return;
        }
        //处理clientID为null或长度为0的情况，协议P29
        if (connectMessage.getPayload().getClientId() == null || connectMessage.getPayload().getClientId().length() == 0) {
            //clientID为null的时候，cleanSession只能为1,此时给client设置一个随机的，不存在的mac地址为ID，否则，断开连接
            if (connectMessage.getVariableHeader().isCleanSession()) {
                boolean isExist = true;
                String macClientId = StringTool.generalMacString();
                while(isExist){
                    ConnectionDescriptor connectionDescriptor = clients.get(macClientId);
                    if (connectionDescriptor == null) {
                        connectMessage.getPayload().setClientId(macClientId);
                        isExist = false;
                    } else {
                        macClientId = StringTool.generalMacString();
                    }
                }
            } else {
                ConnAckMessage connAckMessage = (ConnAckMessage)MQTTMessageFactory.newMessage(connectMessage.getFixedHeader(),
                        new ConnAckVariableHeader(ConnAckMessage.ConnectionStatus.IDENTIFIER_REJECTED, false),
                        null);
                client.writeAndFlush(connAckMessage);
                client.close();
            }
            return;
        }

        //如果会话中已经存储了这个新连接的ID，就关闭之前的clientID
        if (clients.containsKey(connectMessage.getPayload().getClientId())) {
            Channel oldChannel = clients.get(connectMessage.getPayload().getClientId()).getClient();
            boolean cleanSession = NettyAttrManager.getAttrCleanSession(oldChannel);
            if (cleanSession) {
                cleanSession(connectMessage.getPayload().getClientId());
            }
            oldChannel.close();
        }

        //新客户端连接加入client的维护列表中
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(
                connectMessage.getPayload().getClientId(),
                client,
                connectMessage.getVariableHeader().isCleanSession());
        this.clients.put(connectMessage.getPayload().getClientId(), connectionDescriptor);
        //处理心跳包时间，把心跳包时长和一些其他属性都添加到会话中，方便以后使用
        int keepAlive = connectMessage.getVariableHeader().getKeepAlive();
        LOG.debug("连接的心跳包时长是 {" + keepAlive + "} s");
        NettyAttrManager.setAttrClientId(client, connectMessage.getPayload().getClientId());
        NettyAttrManager.setAttrCleanSession(client, connectMessage.getVariableHeader().isCleanSession());
        //协议P29规定，在超过1.5个keepAlive的时间以上没收到心跳包PingReq，就断开连接(但这里要注意把单位是s转为ms)
        NettyAttrManager.setAttrKeepAlive(client, keepAlive);
        //添加心跳机制处理的Handler
        client.pipeline().addFirst("idleStateHandler", new IdleStateHandler(keepAlive, Integer.MAX_VALUE, Integer.MAX_VALUE, TimeUnit.SECONDS));

        //处理Will flag（遗嘱信息）,协议P26
        if (connectMessage.getVariableHeader().isHasWill()) {
            Qos willQos = connectMessage.getVariableHeader().getWillQoS();
            ByteBuf willPayload = Unpooled.buffer().writeBytes(connectMessage.getPayload().getWillMessage().getBytes());
            WillMessage willMessage = new WillMessage(connectMessage.getPayload().getWillTopic(),
                    willPayload,
                    connectMessage.getVariableHeader().isWillRetain(),
                    willQos);
            willStore.put(connectMessage.getPayload().getClientId(), willMessage);
        }

        //处理身份验证（userNameFlag和passwordFlag）
        if (connectMessage.getVariableHeader().isHasUsername() &&
                connectMessage.getVariableHeader().isHasPassword()) {
            String userName = connectMessage.getPayload().getUsername();
            String password = connectMessage.getPayload().getPassword();
            if(!authenticator.checkValid(userName, password)){
                ConnAckMessage connAckMessage = (ConnAckMessage)MQTTMessageFactory.newMessage(
                        FixedHeader.getConnAckFixedHeader(),
                        new ConnAckVariableHeader(ConnAckMessage.ConnectionStatus.BAD_USERNAME_OR_PASSWORD, false),
                        null);
                client.writeAndFlush(connAckMessage);
                return;
            }
        }

        //处理cleanSession为1的情况
        if (connectMessage.getVariableHeader().isCleanSession()) {
            //移除所有之前的session并开启一个新的，并且原先保存的subscribe之类的都得从服务器删掉
            cleanSession(connectMessage.getPayload().getClientId());
        }

        //处理回写的CONNACK,并回写，协议P29
        ConnAckMessage okResp = null;
        //协议32,session present的处理
        if (!connectMessage.getVariableHeader().isCleanSession() &&
                sessionStore.searchSubscriptions(connectMessage.getPayload().getClientId())) {
            okResp = (ConnAckMessage)MQTTMessageFactory.newMessage(
                    FixedHeader.getConnAckFixedHeader(),
                    new ConnAckVariableHeader(ConnAckMessage.ConnectionStatus.ACCEPTED, true),
                    null);
        } else {
            okResp = (ConnAckMessage)MQTTMessageFactory.newMessage(
                    FixedHeader.getConnAckFixedHeader(),
                    new ConnAckVariableHeader(ConnAckMessage.ConnectionStatus.ACCEPTED, false),
                    null);
        }
        client.writeAndFlush(okResp);
        LOG.info("CONNACK处理完毕并成功发送");
        LOG.info("连接的客户端clientID="+connectMessage.getPayload().getClientId()+", " +
                "cleanSession为"+connectMessage.getVariableHeader().isCleanSession());
        //如果cleanSession=0,需要在重连的时候重发同一clientID存储在服务端的离线信息
        if (!connectMessage.getVariableHeader().isCleanSession()) {
            //force the republish of stored QoS1 and QoS2
            republishMessage(connectMessage.getPayload().getClientId());
        }
    }

    /**
     * 处理协议的publish消息类型,该方法先把public需要的事件提取出来
     *
     * @param client
     * @param publishMessage
     */
    public void processPublic(Channel client, PublishMessage publishMessage){
        LOG.info("处理publish的数据");
        String clientID = NettyAttrManager.getAttrClientId(client);
        final String topic = publishMessage.getVariableHeader().getTopic();
        final Qos qos = publishMessage.getFixedHeader().getQos();
        final ByteBuf message = publishMessage.getPayload();
        final int packgeID = publishMessage.getVariableHeader().getPackageID();
        final boolean retain = publishMessage.getFixedHeader().isRetain();

        processPublic(clientID, topic, qos, retain, message, packgeID);
    }

    /**
     * 处理遗言消息的发送
     *
     * @param client
     * @param willMessage
     */
    public void processPublic(Channel client, WillMessage willMessage){
        LOG.info("处理遗言的publish数据");
        String clientID = NettyAttrManager.getAttrClientId(client);
        final String topic = willMessage.getTopic();
        final Qos qos = willMessage.getQos();
        final ByteBuf message = willMessage.getPayload();
        final boolean retain = willMessage.isRetained();

        processPublic(clientID, topic, qos, retain, message, null);
    }

    /**
     * 根据协议进行具体的处理，处理不同的Qos等级下的public事件
     *
     * @param clientID
     * @param topic
     * @param qos
     * @param recRetain
     * @param message
     * @param recPackgeID 此包ID只是客户端传过来的，用于发回pubAck用，发送给其他客户端的包ID，需要重新生成
     */
    private void processPublic(String clientID, String topic, Qos qos, boolean recRetain, ByteBuf message, Integer recPackgeID){
        LOG.info("接收public消息:{clientID="+clientID+",Qos="+qos+",topic="+topic+",packageID="+recPackgeID+"}");
        String publishKey = null;
//		int sendPackageID = PackageIDManager.getNextMessageId();

        //根据协议P34，Qos=3的时候，就关闭连接
        if (qos == Qos.RESERVE) {
            clients.get(clientID).getClient().close();
        }

        //根据协议P52，qos=0, Dup=0, 则把消息发送给所有注册的客户端即可
        if (qos == Qos.AT_MOST_ONCE) {
            boolean dup = false;
            boolean retain = false;
            sendPublishMessage(topic, qos, message, retain, dup);
        }

        //根据协议P53，publish的接受者需要发送该publish(Qos=1,Dup=0)消息给其他客户端，然后发送pubAck给该客户端。
        //发送该publish消息时候，按此流程： 存储消息→发送给所有人→等待pubAck到来→删除消息
        if (qos == Qos.AT_LEAST_ONCE) {
            boolean retain = false;
            boolean dup = false;

            sendPublishMessage(topic, qos, message, retain, dup);
            sendPubAck(clientID, recPackgeID);
        }

        //根据协议P54，P55
        //接收端：publish接收消息→存储包ID→发给其他客户端→发回pubRec→收到pubRel→抛弃第二步存储的包ID→发回pubcomp
        //发送端：存储消息→发送publish(Qos=2,Dup=0)→收到pubRec→抛弃第一步存储的消息→存储pubRec的包ID→发送pubRel→收到pubcomp→抛弃pubRec包ID的存储
        if (qos == Qos.EXACTLY_ONCE) {
            boolean dup = false;
            boolean retain = false;
            messagesStore.storePublicPackgeID(clientID, recPackgeID);
            sendPublishMessage(topic, qos, message, retain, dup);
            sendPubRec(clientID, recPackgeID);
        }

        //处理消息是否保留，注：publish报文中的主题名不能包含通配符(协议P35)，所以retain中保存的主题名不会有通配符
        if (recRetain) {
            if (qos == Qos.AT_MOST_ONCE) {
                messagesStore.cleanRetained(topic);
            } else {
                messagesStore.storeRetained(topic, message, qos);
            }
        }
    }

    /**
     * 处理协议的pubAck消息类型
     *
     * @param client
     * @param pubAckVariableMessage
     */
    public void processPubAck(Channel client, PackageIdVariableHeader pubAckVariableMessage){
        String clientID = NettyAttrManager.getAttrClientId(client);
        int pacakgeID = pubAckVariableMessage.getPackageID();
        String publishKey = String.format("%s%d", clientID, pacakgeID);
        //取消Publish重传任务
        QuartzManager.removeJob(publishKey, "publish", publishKey, "publish");
        //删除临时存储用于重发的Publish消息
        messagesStore.removeQosPublishMessage(publishKey);
        //最后把使用完的包ID释放掉
        PackageIDManager.releaseMessageId(pacakgeID);
    }

    /**
     * 处理协议的pubRec消息类型
     *
     * @param client
     * @param pubRecVariableMessage
     */
    public void processPubRec(Channel client, PackageIdVariableHeader pubRecVariableMessage){
        String clientID = NettyAttrManager.getAttrClientId(client);
        int packageID = pubRecVariableMessage.getPackageID();
        String publishKey = String.format("%s%d", clientID, packageID);

        //取消Publish重传任务,同时删除对应的值
        QuartzManager.removeJob(publishKey, "publish", publishKey, "publish");
        messagesStore.removeQosPublishMessage(publishKey);
        //此处须额外处理，根据不同的事件，处理不同的包ID
        messagesStore.storePubRecPackgeID(clientID, packageID);
        //组装PubRel事件后，存储PubRel事件，并发回PubRel
        PubRelEvent pubRelEvent = new PubRelEvent(clientID, packageID);
        //此处的Key和Publish的key一致
        messagesStore.storePubRelMessage(publishKey, pubRelEvent);
        //发回PubRel
        sendPubRel(clientID, packageID);
        //开启PubRel重传事件
        Map<String, Object> jobParam = new HashMap<String, Object>();
        jobParam.put("ProtocolProcess", this);
        jobParam.put("pubRelKey", publishKey);
        QuartzManager.addJob(publishKey, "pubRel", publishKey, "pubRel", RePubRelJob.class, 10, 2, jobParam);
    }

    /**
     * 处理协议的pubRel消息类型
     *
     * @param client
     * @param pubRelVariableMessage
     */
    public void processPubRel(Channel client, PackageIdVariableHeader pubRelVariableMessage){
        String clientID = NettyAttrManager.getAttrClientId(client);
        //删除的是接收端的包ID
        int pacakgeID = pubRelVariableMessage.getPackageID();

        messagesStore.removePublicPackgeID(clientID);
        sendPubComp(clientID, pacakgeID);
    }

    /**
     * 处理协议的pubComp消息类型
     *
     * @param client
     * @param pubcompVariableMessage
     */
    public void processPubComp(Channel client, PackageIdVariableHeader pubcompVariableMessage){
        String clientID = NettyAttrManager.getAttrClientId(client);
        int packaageID = pubcompVariableMessage.getPackageID();
        String pubRelkey = String.format("%s%d", clientID, packaageID);

        //删除存储的PubRec包ID
        messagesStore.removePubRecPackgeID(clientID);
        //取消PubRel的重传任务，删除临时存储的PubRel事件
        QuartzManager.removeJob(pubRelkey, "pubRel", pubRelkey, "pubRel");
        messagesStore.removePubRelMessage(pubRelkey);
        //最后把使用完的包ID释放掉
        PackageIDManager.releaseMessageId(packaageID);
    }

    /**
     * 处理协议的subscribe消息类型
     *
     * @param client
     * @param subscribeMessage
     */
    public void processSubscribe(Channel client, SubscribeMessage subscribeMessage) {
        String clientID = NettyAttrManager.getAttrClientId(client);
        boolean cleanSession = NettyAttrManager.getAttrCleanSession(client);
        LOG.info("处理subscribe数据包，客户端ID={"+clientID+"},cleanSession={"+cleanSession+"}");
        //一条subscribeMessage信息可能包含多个Topic和Qos
        List<TopicSubscribe> topicSubscribes = subscribeMessage.getPayload().getTopicSubscribes();

        List<Integer> grantedQosLevel = new ArrayList<Integer>();
        //依次处理订阅
        for (TopicSubscribe topicSubscribe : topicSubscribes) {
            String topicFilter = topicSubscribe.getTopicFilter();
            Qos qos = topicSubscribe.getQos();
            Subscription newSubscription = new Subscription(clientID, topicFilter, qos, cleanSession);
            //订阅新的订阅
            subscribeSingleTopic(newSubscription, topicFilter);

            //生成suback荷载
            grantedQosLevel.add(qos.value());
        }

        SubAckMessage subAckMessage = (SubAckMessage) MQTTMessageFactory.newMessage(
                FixedHeader.getSubAckFixedHeader(),
                new PackageIdVariableHeader(subscribeMessage.getVariableHeader().getPackageID()),
                new SubAckPayload(grantedQosLevel));

        LOG.info("回写subAck消息给订阅者，包ID={"+subscribeMessage.getVariableHeader().getPackageID()+"}");
        client.writeAndFlush(subAckMessage);
    }


    /**
     * 处理协议的unSubscribe消息类型
     *
     * @param client
     * @param unSubscribeMessage
     */
    public void processUnSubscribe(Channel client, UnSubscribeMessage unSubscribeMessage){
        String clientID = NettyAttrManager.getAttrClientId(client);
        int packageID = unSubscribeMessage.getVariableHeader().getPackageID();
        LOG.info("处理unSubscribe数据包，客户端ID={"+clientID+"}");
        List<String> topicFilters = unSubscribeMessage.getPayload().getTopics();
        for (String topic : topicFilters) {
            //取消订阅树里的订阅
            subscribeStore.removeSubscription(topic, clientID);
            sessionStore.removeSubscription(topic, clientID);
        }

        Message unSubAckMessage = MQTTMessageFactory.newMessage(
                FixedHeader.getUnSubAckFixedHeader(),
                new PackageIdVariableHeader(packageID),
                null);
        LOG.info("回写unSubAck信息给客户端，包ID为{"+packageID+"}");
        client.writeAndFlush(unSubAckMessage);
    }

    /**
     * 处理协议的pingReq消息类型
     *
     * @param client
     * @param pingReqMessage
     */
    public void processPingReq(Channel client, Message pingReqMessage){
        LOG.info("收到心跳包");
        Message pingRespMessage = MQTTMessageFactory.newMessage(
                FixedHeader.getPingRespFixedHeader(),
                null,
                null);
        //重置心跳包计时器
        client.writeAndFlush(pingRespMessage);
    }

    /**
     * 处理协议的disconnect消息类型
     * @param client
     * @param disconnectMessage
     * @author zer0
     * @version 1.0
     * @date 2015-5-24
     */
    public void processDisconnet(Channel client, Message disconnectMessage){
        String clientID = NettyAttrManager.getAttrClientId(client);
        boolean cleanSession = NettyAttrManager.getAttrCleanSession(client);
        if (cleanSession) {
            cleanSession(clientID);
        }

        willStore.remove(clientID);

        this.clients.remove(clientID);
        client.close();
    }

    /**
     * 在客户端重连以后，针对QoS1和Qos2的消息，重发存储的离线消息
     *
     * @param clientID
     */
    private void republishMessage(String clientID){
        //取出需要重发的消息列表
        //查看消息列表是否为空，为空则返回
        //不为空则依次发送消息并从会话中删除此消息
        List<PublishEvent> publishedEvents = messagesStore.listMessagesInSession(clientID);
        if (publishedEvents.isEmpty()) {
            LOG.info("没有客户端{"+clientID+"}存储的离线消息");
            return;
        }

        LOG.info("重发客户端{"+ clientID +"}存储的离线消息");
        for (PublishEvent pubEvent : publishedEvents) {
            boolean dup = true;
            sendPublishMessage(pubEvent.getClientID(),
                    pubEvent.getTopic(),
                    pubEvent.getQos(),
                    Unpooled.buffer().writeBytes(pubEvent.getMessage()),
                    pubEvent.isRetain(),
                    pubEvent.getPackgeID(),
                    dup);
            messagesStore.removeMessageInSessionForPublish(clientID, pubEvent.getPackgeID());
        }
    }

    /**
     * 取出所有匹配topic的客户端，然后发送public消息给客户端
     *
     * @param topic
     * @param originQos
     * @param message
     * @param retain
     * @param dup
     */
    private void sendPublishMessage(String topic, Qos originQos, ByteBuf message, boolean retain, boolean dup){
        for (final Subscription sub : subscribeStore.getClientListFromTopic(topic)) {

            String clientID = sub.getClientID();
            Integer sendPackageID = PackageIDManager.getNextMessageId();
            String publishKey = String.format("%s%d", clientID, sendPackageID);
            Qos qos = originQos;

            //协议P43提到， 假设请求的QoS级别被授权，客户端接收的PUBLISH消息的QoS级别小于或等于这个级别，PUBLISH 消息的级别取决于发布者的原始消息的QoS级别
            if (originQos.ordinal() > sub.getRequestedQos().ordinal()) {
                qos = sub.getRequestedQos();
            }

            PublishMessage publishMessage = (PublishMessage) MQTTMessageFactory.newMessage(
                    FixedHeader.getPublishFixedHeader(dup, qos, retain),
                    new PublishVariableHeader(topic, sendPackageID),
                    message);

            if (this.clients == null) {
                throw new RuntimeException("内部错误，clients为null");
            } else {
                LOG.debug("clients为{"+this.clients+"}");
            }

            if (this.clients.get(clientID) == null) {
                throw new RuntimeException("不能从会话列表{"+this.clients+"}中找到clientID:{"+clientID+"}");
            } else {
                LOG.debug("从会话列表{"+this.clients+"}查找到clientID:{"+clientID+"}");
            }

            if (originQos == Qos.AT_MOST_ONCE) {
                publishMessage = (PublishMessage) MQTTMessageFactory.newMessage(
                        FixedHeader.getPublishFixedHeader(dup, qos, retain),
                        new PublishVariableHeader(topic),
                        message);
                //从会话列表中取出会话，然后通过此会话发送publish消息
                this.clients.get(clientID).getClient().writeAndFlush(publishMessage);
            }else {
                publishKey = String.format("%s%d", clientID, sendPackageID);//针对每个重生成key，保证消息ID不会重复
                //将ByteBuf转变为byte[]
                byte[] messageBytes = new byte[message.readableBytes()];
                message.getBytes(message.readerIndex(), messageBytes);
                PublishEvent storePublishEvent = new PublishEvent(topic, qos, messageBytes, retain, clientID, sendPackageID);

                //从会话列表中取出会话，然后通过此会话发送publish消息
                this.clients.get(clientID).getClient().writeAndFlush(publishMessage);
                //存临时Publish消息，用于重发
                messagesStore.storeQosPublishMessage(publishKey, storePublishEvent);
                //开启Publish重传任务，在制定时间内未收到PubAck包则重传该条Publish信息
                Map<String, Object> jobParam = new HashMap<String, Object>();
                jobParam.put("ProtocolProcess", this);
                jobParam.put("publishKey", publishKey);
                QuartzManager.addJob(publishKey, "publish", publishKey, "publish", RePublishJob.class, 10, 2, jobParam);
            }

            LOG.info("服务器发送消息给客户端{"+clientID+"},topic{"+topic+"},qos{"+qos+"}");

            if (!sub.isCleanSession()) {
                //将ByteBuf转变为byte[]
                byte[] messageBytes = new byte[message.readableBytes()];
                message.getBytes(message.readerIndex(), messageBytes);
                PublishEvent newPublishEvt = new PublishEvent(topic, qos, messageBytes,
                        retain, sub.getClientID(),
                        sendPackageID != null ? sendPackageID : 0);
                messagesStore.storeMessageToSessionForPublish(newPublishEvt);
            }


        }
    }

    /**
     * 发送publish消息给指定ID的客户端
     *
     * @param clientID
     * @param topic
     * @param qos
     * @param message
     * @param retain
     * @param packageID
     * @param dup
     */
    private void sendPublishMessage(String clientID, String topic, Qos qos, ByteBuf message, boolean retain, Integer packageID, boolean dup){
        LOG.info("发送pulicMessage给指定客户端");

        String publishKey = String.format("%s%d", clientID, packageID);

        PublishMessage publishMessage = (PublishMessage) MQTTMessageFactory.newMessage(
                FixedHeader.getPublishFixedHeader(dup, qos, retain),
                new PublishVariableHeader(topic, packageID),
                message);

        if (this.clients == null) {
            throw new RuntimeException("内部错误，clients为null");
        } else {
            LOG.debug("clients为{"+this.clients+"}");
        }

        if (this.clients.get(clientID) == null) {
            throw new RuntimeException("不能从会话列表{"+this.clients+"}中找到clientID:{"+clientID+"}");
        } else {
            LOG.debug("从会话列表{"+this.clients+"}查找到clientID:{"+clientID+"}");
        }

        if (qos == Qos.AT_MOST_ONCE) {
            publishMessage = (PublishMessage) MQTTMessageFactory.newMessage(
                    FixedHeader.getPublishFixedHeader(dup, qos, retain),
                    new PublishVariableHeader(topic),
                    message);
            //从会话列表中取出会话，然后通过此会话发送publish消息
            this.clients.get(clientID).getClient().writeAndFlush(publishMessage);
        }else {
            publishKey = String.format("%s%d", clientID, packageID);//针对每个重生成key，保证消息ID不会重复
            //将ByteBuf转变为byte[]
            byte[] messageBytes = new byte[message.readableBytes()];
            message.getBytes(message.readerIndex(), messageBytes);
            PublishEvent storePublishEvent = new PublishEvent(topic, qos, messageBytes, retain, clientID, packageID);

            //从会话列表中取出会话，然后通过此会话发送publish消息
            this.clients.get(clientID).getClient().writeAndFlush(publishMessage);
            //存临时Publish消息，用于重发
            messagesStore.storeQosPublishMessage(publishKey, storePublishEvent);
            //开启Publish重传任务，在制定时间内未收到PubAck包则重传该条Publish信息
            Map<String, Object> jobParam = new HashMap<String, Object>();
            jobParam.put("ProtocolProcess", this);
            jobParam.put("publishKey", publishKey);
            QuartzManager.addJob(publishKey, "publish", publishKey, "publish", RePublishJob.class, 10, 2, jobParam);
        }
    }

    /**
     * 发送保存的Retain消息
     * @param clientID
     * @param topic
     * @param qos
     * @param message
     * @param retain
     */
    private void sendPublishMessage(String clientID, String topic, Qos qos, ByteBuf message, boolean retain){
        int packageID = PackageIDManager.getNextMessageId();
        sendPublishMessage(clientID, topic, qos, message, retain, packageID, false);
    }

    /**
     * 在未收到对应包的情况下，重传PubRel消息
     * @param pubRelKey
     */
    public void reUnKnowPubRelMessage(String pubRelKey){
        PubRelEvent pubEvent = messagesStore.searchPubRelMessage(pubRelKey);
        LOG.info("重发PubRelKey为{"+ pubRelKey +"}的PubRel离线消息");
        sendPubRel(pubEvent.getClientID(), pubEvent.getPackgeID());
    }

    /**
     * 在未收到对应包的情况下，重传Publish消息
     * @param publishKey
     */
    public void reUnKnowPublishMessage(String publishKey){
        PublishEvent pubEvent = messagesStore.searchQosPublishMessage(publishKey);
        LOG.info("重发PublishKey为{"+ publishKey +"}的Publish离线消息");
        boolean dup = true;
        PublishMessage publishMessage = (PublishMessage) MQTTMessageFactory.newMessage(
                FixedHeader.getPublishFixedHeader(dup, pubEvent.getQos(), pubEvent.isRetain()),
                new PublishVariableHeader(pubEvent.getTopic(), pubEvent.getPackgeID()),
                Unpooled.buffer().writeBytes(pubEvent.getMessage()));
        //从会话列表中取出会话，然后通过此会话发送publish消息
        this.clients.get(pubEvent.getClientID()).getClient().writeAndFlush(publishMessage);
    }

    /**
     * 清除会话，除了要从订阅树中删掉会话信息，还要从会话存储中删除会话信息
     *
     * @param clientId
     */
    private void cleanSession(String clientId){
        subscribeStore.removeForClient(clientId);
        sessionStore.wipeSubscriptions(clientId);
    }

    /**
     *回写PubAck消息给发来publish的客户端
     *
     * @param clientID
     * @param packageID
     */
    private void sendPubAck(String clientID, Integer packageID) {
        LOG.info("发送PubAck消息给客户端");

        Message pubAckMessage = MQTTMessageFactory.newMessage(
                FixedHeader.getPubAckFixedHeader(),
                new PackageIdVariableHeader(packageID),
                null);

        try {
            if (this.clients == null) {
                throw new RuntimeException("内部错误，clients为null");
            } else {
                LOG.debug("clients为{"+this.clients+"}");
            }

            if (this.clients.get(clientID) == null) {
                throw new RuntimeException("不能从会话列表{"+this.clients+"}中找到clientID:{"+clientID+"}");
            } else {
                LOG.debug("从会话列表{"+this.clients+"}查找到clientID:{"+clientID+"}");
            }

            this.clients.get(clientID).getClient().writeAndFlush(pubAckMessage);
        }catch(Throwable t) {
            LOG.error(null, t);
        }
    }

    /**
     * 回写PubRec消息给发来publish的客户端
     *
     * @param clientID
     * @param packageID
     */
    private void sendPubRec(String clientID, Integer packageID) {
        LOG.trace("发送PubRec消息给客户端");

        Message pubRecMessage = MQTTMessageFactory.newMessage(
                FixedHeader.getPubAckFixedHeader(),
                new PackageIdVariableHeader(packageID),
                null);

        try {
            if (this.clients == null) {
                throw new RuntimeException("内部错误，clients为null");
            } else {
                LOG.debug("clients为{"+this.clients+"}");
            }

            if (this.clients.get(clientID) == null) {
                throw new RuntimeException("不能从会话列表{"+this.clients+"}中找到clientID:{"+clientID+"}");
            } else {
                LOG.debug("从会话列表{"+this.clients+"}查找到clientID:{"+clientID+"}");
            }

            this.clients.get(clientID).getClient().writeAndFlush(pubRecMessage);
        }catch(Throwable t) {
            LOG.error(null, t);
        }
    }

    /**
     * 回写PubRel消息给发来publish的客户端
     *
     * @param clientID
     * @param packageID
     * @author zer0
     * @version 1.0
     * @date 2015-5-23
     */
    private void sendPubRel(String clientID, Integer packageID) {
        LOG.trace("发送PubRel消息给客户端");

        Message pubRelMessage = MQTTMessageFactory.newMessage(
                FixedHeader.getPubAckFixedHeader(),
                new PackageIdVariableHeader(packageID),
                null);

        try {
            if (this.clients == null) {
                throw new RuntimeException("内部错误，clients为null");
            } else {
                LOG.debug("clients为{"+this.clients+"}");
            }

            if (this.clients.get(clientID) == null) {
                throw new RuntimeException("不能从会话列表{"+this.clients+"}中找到clientID:{"+clientID+"}");
            } else {
                LOG.debug("从会话列表{"+this.clients+"}查找到clientID:{"+clientID+"}");
            }

            this.clients.get(clientID).getClient().writeAndFlush(pubRelMessage);
        }catch(Throwable t) {
            LOG.error(null, t);
        }
    }

    /**
     * 回写PubComp消息给发来publish的客户端
     *
     * @param clientID
     * @param packageID
     */
    private void sendPubComp(String clientID, Integer packageID) {
        LOG.trace("发送PubComp消息给客户端");

        Message pubcompMessage = MQTTMessageFactory.newMessage(
                FixedHeader.getPubAckFixedHeader(),
                new PackageIdVariableHeader(packageID),
                null);

        try {
            if (this.clients == null) {
                throw new RuntimeException("内部错误，clients为null");
            } else {
                LOG.debug("clients为{"+this.clients+"}");
            }

            if (this.clients.get(clientID) == null) {
                throw new RuntimeException("不能从会话列表{"+this.clients+"}中找到clientID:{"+clientID+"}");
            } else {
                LOG.debug("从会话列表{"+this.clients+"}查找到clientID:{"+clientID+"}");
            }

            this.clients.get(clientID).getClient().writeAndFlush(pubcompMessage);
        }catch(Throwable t) {
            LOG.error(null, t);
        }
    }

    /**
     * 处理一个单一订阅，存储到会话和订阅数
     *
     * @param newSubscription
     * @param topic
     */
    private void subscribeSingleTopic(Subscription newSubscription, final String topic){
        LOG.info("订阅topic{"+topic+"},Qos为{"+newSubscription.getRequestedQos()+"}");
        String clientID = newSubscription.getClientID();
        sessionStore.addNewSubscription(newSubscription, clientID);
        subscribeStore.addSubscrpition(newSubscription);
        //TODO 此处还需要将此订阅之前存储的信息发出去
        Collection<IMessagesStore.StoredMessage> messages = messagesStore.searchRetained(topic);
        for (IMessagesStore.StoredMessage storedMsg : messages) {
            LOG.debug("send publish message for topic {" + topic + "}");
            sendPublishMessage(newSubscription.getClientID(), storedMsg.getTopic(), storedMsg.getQos(), Unpooled.buffer().writeBytes(storedMsg.getPayload()), true);
        }
    }




    static  final class WillMessage{
        private final String topic;
        private final ByteBuf payload;
        private final boolean retained;
        private final Qos qos;

        public WillMessage(String topic, ByteBuf payload, boolean retained, Qos qos) {
            this.topic = topic;
            this.payload = payload;
            this.retained = retained;
            this.qos = qos;
        }

        public String getTopic() {
            return topic;
        }

        public ByteBuf getPayload() {
            return payload;
        }

        public boolean isRetained() {
            return retained;
        }

        public Qos getQos() {
            return qos;
        }
    }

}
