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
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片编辑处理器（ WebSocket 处理器）
 * 定义 WebSocket 处理器类，在连接成功、连接关闭、接收到客户端消息时进行相应的处理
 */
@Component
@Slf4j
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;

    // 记录正在编辑的图片的 id 和用户 id
    private final Map<Long, Long> pictureUsers = new ConcurrentHashMap<>();

    // 记录正在编辑的图片的 id 和 WebSocketSession
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    @Resource
    private UserService userService;

    /**
     * 连接成功
     *
     * @param session WebSocketSession
     * @throws Exception 异常
     */
    @Override
    public void afterConnectionEstablished(
            @NotNull WebSocketSession session)
            throws Exception {
        // 获取公共属性
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        // 保存会话信息
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);

        // 构造图片编辑响应消息
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        // 广播给同一张图片的用户
        broadcast(pictureId, pictureEditResponseMessage);
    }

    /**
     * 接收到客户端消息（根据不同消息类别执行不同处理）
     *
     * @param session WebSocketSession
     * @param message 文本消息
     */
    @Override
    public void handleTextMessage(
            @NotNull WebSocketSession session,
            @NotNull TextMessage message)
            throws Exception {
        // 1、获取消息类别
        // 解析消息为图片编辑请求消息
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        // 获取图片编辑请求消息的类型
        String type = pictureEditRequestMessage.getType();
        // 将 type 转为枚举类型
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.getEnumByValue(type);
        // 从 session 中获取公共属性
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");
        // 2、生产消息
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }

    /**
     * 连接关闭
     *
     * @param session WebSocketSession
     * @param status  关闭状态
     * @throws Exception 异常
     */
    @Override
    public void afterConnectionClosed(@NotNull WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        // 1、获取公共属性
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");

        // 2、移除当前用户编辑状态和 session
        // 移除当前用户编辑状态
        handleExitEditMessage(null, session, user, pictureId);
        // 移除当前用户 session
        Set<WebSocketSession> webSocketSessions = pictureSessions.get(pictureId);
        if (webSocketSessions != null) {
            webSocketSessions.remove(session);
            // 如果没有用户编辑该图片，则移除该图片
            if (webSocketSessions.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        // 3、构造图片编辑响应消息
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));

        // 4、广播给同一张图片的用户
        broadcast(pictureId, pictureEditResponseMessage);
    }

    /**
     * 广播给协作者
     *
     * @param pictureId                  图片 id
     * @param pictureEditResponseMessage 图片编辑响应消息
     * @param excludeSession             排除的 WebSocketSession
     * @throws Exception 异常
     */
    public void broadcast(
            Long pictureId,
            PictureEditResponseMessage pictureEditResponseMessage,
            WebSocketSession excludeSession)
            throws Exception {
        // 1、获取正在编辑的图片的 webSocketSessions
        Set<WebSocketSession> webSocketSessions = pictureSessions.get(pictureId);
        // 2、如果不为空，则遍历 webSocketSessions，发送消息给每个 WebSocketSession
        if (CollUtil.isNotEmpty(webSocketSessions)) {
            // 3、解决精度丢失问题
            // 创建 ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化：将 Long 类型转为 String
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
            objectMapper.registerModule(module);
            // 序列化为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            // 将 message 转为 TextMessage（ WebSocketMessage 的子类）
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : webSocketSessions) {
                // 排除 excludeSession
                if (!session.equals(excludeSession)) {
                    continue;
                }
                // 发送消息给 WebSocket
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    /**
     * 广播给所有人
     *
     * @param pictureId                  图片 id
     * @param pictureEditResponseMessage 图片编辑响应消息
     * @throws Exception 异常
     */
    public void broadcast(
            Long pictureId,
            PictureEditResponseMessage pictureEditResponseMessage)
            throws Exception {
        broadcast(pictureId, pictureEditResponseMessage, null);
    }

    /**
     * 处理进入编辑消息
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session                   WebSocketSession
     * @param user                      用户
     * @param pictureId                 图片 id
     */
    public void handleEnterEditMessage(
            PictureEditRequestMessage pictureEditRequestMessage,
            WebSocketSession session,
            User user,
            Long pictureId) throws Exception {
        // 没有用户正在编辑该图片，才能进入编辑
        if (!pictureUsers.containsKey(pictureId)) {
            // 设置编辑用户为当前用户
            pictureUsers.put(pictureId, user.getId());
            // 构造图片编辑响应消息
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 广播给同一张图片的用户
            broadcast(pictureId, pictureEditResponseMessage);
        }
    }

    /**
     * 处理编辑动作消息（将该操作同步给除了当前用户之外的其他客户端）
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session                   WebSocketSession
     * @param user                      用户
     * @param pictureId                 图片 id
     */
    public void handleEditActionMessage(
            PictureEditRequestMessage pictureEditRequestMessage,
            WebSocketSession session,
            User user,
            Long pictureId)
            throws Exception {
        // 1、获取公共属性
        // 获取当前编辑用户
        Long editUserId = pictureUsers.get(pictureId);
        // 获取当前编辑类型
        String editAction = pictureEditRequestMessage.getEditAction();
        // 将编辑类型转为枚举值
        PictureEditActionEnum pictureEditActionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        // 判空
        if (pictureEditActionEnum == null) {
            log.info("编辑类型错误");
        }

        // 确认是当前编辑者
        if (editUserId != null && editUserId.equals(user.getId())) {
            // 2、构造图片编辑响应消息
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s执行%s", user.getUserName(), pictureEditActionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 3、广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcast(pictureId, pictureEditResponseMessage, session);
        }
    }

    /**
     * 处理退出编辑消息
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session                   WebSocketSession
     * @param user                      用户
     * @param pictureId                 图片 id
     */
    public void handleExitEditMessage(
            PictureEditRequestMessage pictureEditRequestMessage,
            WebSocketSession session,
            User user,
            Long pictureId)
            throws Exception {
        // 1、获取当前编辑用户
        Long editUserId = pictureUsers.get(pictureId);
        // 确定是当前编辑者
        if (editUserId != null && editUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            pictureUsers.remove(pictureId);
            // 2、构造图片编辑响应消息
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));

            // 3、广播给同一张图片的用户
            broadcast(pictureId, pictureEditResponseMessage);
        }
    }
}
