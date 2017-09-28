package com.jonex.push.protocol;


import com.jonex.push.event.PubRelEvent;
import com.jonex.push.event.PublishEvent;
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
import com.jonex.push.util.StringTool;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
     *  回写PubRel消息给发来publish的客户端
     *
     * @param clientID
     * @param packageID
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
     * 清除会话，除了要从订阅树中删掉会话信息，还要从会话存储中删除会话信息
     *
     * @param clientId
     */
    private void cleanSession(String clientId){
        subscribeStore.removeForClient(clientId);
        sessionStore.wipeSubscriptions(clientId);
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
