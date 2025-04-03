package com.eureka.picwavebackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.eureka.picwavebackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.eureka.picwavebackend.manager.websocket.model.PictureEditActionEnum;
import com.eureka.picwavebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.eureka.picwavebackend.manager.websocket.model.PictureEditRequestMessage;
import com.eureka.picwavebackend.manager.websocket.model.PictureEditResponseMessage;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 处理器（图片编辑处理）
 * 定义 WebSocket 处理器类，在连接成功、连接关闭、接收到客户端消息时进行相应的处理
 */
@Component
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;

    /**
     * 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 id
     * 保存当前正在编辑的用户 id，执行编辑操作、进入或退出编辑时都会校验
     */
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    /**
     * 每张图片的会话集合，key: pictureId, value: 用户会话集合
     * 保存参与编辑图片的用户 WebSocket 会话的集合
     */
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    /**
     * 广播消息
     *
     * @param pictureId                  图片 id
     * @param pictureEditResponseMessage 图片编辑响应消息
     * @param excludeSession             排除会话
     * @throws Exception 异常
     */
    private void broadcastToPicture(Long pictureId,
                                    PictureEditResponseMessage pictureEditResponseMessage,
                                    WebSocketSession excludeSession) throws Exception {
        // 1、获取图片会话集合
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        // 校验图片会话集合是否为空
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 解决丢失精度问题
            ObjectMapper objectMapper = new ObjectMapper();
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(module);
            // 将图片编辑响应消息转换为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            // 创建文本消息
            TextMessage textMessage = new TextMessage(message);
            // 2、遍历图片会话集合，发送消息
            for (WebSocketSession session : sessionSet) {
                // 排除会话不发送
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    /**
     * 全部广播
     *
     * @param pictureId                  图片 id
     * @param pictureEditResponseMessage 图片编辑响应消息
     * @throws Exception 异常
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    /**
     * 连接成功
     *
     * @param session 会话
     * @throws Exception 异常
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 1、保存会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);

        // 构造响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        // 2、广播给同一张图片的用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 接收到客户端消息
     *
     * @param session 会话
     * @param message 消息
     * @throws Exception 异常
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 将消息解析为 PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        // 从 Session 属性中获取公共参数
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        // 生产消息
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

    /**
     * 处理进入编辑消息
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session                   会话
     * @param user                      用户
     * @param pictureId                 图片 id
     * @throws Exception 异常
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage,
                                       WebSocketSession session,
                                       User user,
                                       Long pictureId) throws Exception {
        // 没有用户正在编辑该图片，才能进入编辑
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 1、设置当前用户为编辑用户
            pictureEditingUsers.put(pictureId, user.getId());

            // 构造响应消息
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 2、广播给同一张图片的用户
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }

    /**
     * 处理编辑操作消息
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session                   会话
     * @param user                      用户
     * @param pictureId                 图片 id
     * @throws Exception 异常
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage,
                                        WebSocketSession session,
                                        User user,
                                        Long pictureId) throws Exception {
        // 获取当前图片的正在编辑用户
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }

        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 1、广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    /**
     * 处理退出编辑消息
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session                   会话
     * @param user                      用户
     * @param pictureId                 图片 id
     * @throws Exception 异常
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage,
                                      WebSocketSession session,
                                      User user,
                                      Long pictureId) throws Exception {
        // 获取当前图片的正在编辑用户 id
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 1、移除当前用户的编辑状态
            pictureEditingUsers.remove(pictureId);

            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 2、广播给同一张图片的用户
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }

    /**
     * 断开连接
     *
     * @param session 会话
     * @param status  关闭状态
     * @throws Exception 异常
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");
        // 1、移除当前用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);

        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        // 响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        // 2、广播给同一张图片的用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

}
