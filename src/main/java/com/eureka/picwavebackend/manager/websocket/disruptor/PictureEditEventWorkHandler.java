package com.eureka.picwavebackend.manager.websocket.disruptor;

import cn.hutool.json.JSONUtil;
import com.eureka.picwavebackend.manager.websocket.PictureEditHandler;
import com.eureka.picwavebackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.eureka.picwavebackend.manager.websocket.model.PictureEditRequestMessage;
import com.eureka.picwavebackend.manager.websocket.model.PictureEditResponseMessage;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.service.UserService;
import com.lmax.disruptor.WorkHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

/**
 * 图片编辑事件处理器（ disruptor 处理器）
 * 事件消费者
 */
@Component
@Slf4j
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    @Resource
    private UserService userService;

    @Resource
    private PictureEditHandler pictureEditHandler;

    /**
     * 消费事件
     *
     * @param pictureEditEvent 图片编辑事件
     * @throws Exception 异常
     */
    @Override
    public void onEvent(PictureEditEvent pictureEditEvent) throws Exception {
        // 1、获取消息类型
        PictureEditRequestMessage pictureEditRequestMessage = pictureEditEvent.getPictureEditRequestMessage();
        WebSocketSession session = pictureEditEvent.getSession();
        User user = pictureEditEvent.getUser();
        Long pictureId = pictureEditEvent.getPictureId();
        // 获取消息类型
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.valueOf(type);

        // 2、调用对应消息处理方法
        switch (pictureEditMessageTypeEnum) {
            case ENTER_EDIT:
                pictureEditHandler.handleEnterEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EDIT_ACTION:
                pictureEditHandler.handleEditActionMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EXIT_EDIT:
                pictureEditHandler.handleExitEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            default:
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                pictureEditResponseMessage.setMessage("消息类型错误");
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
        }

    }
}
