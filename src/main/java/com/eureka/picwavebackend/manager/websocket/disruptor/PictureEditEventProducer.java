package com.eureka.picwavebackend.manager.websocket.disruptor;

import com.eureka.picwavebackend.manager.websocket.model.PictureEditRequestMessage;
import com.eureka.picwavebackend.model.entity.User;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

@Component
@Slf4j
public class PictureEditEventProducer {

    @Resource
    Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    /**
     * 发布事件
     *
     * @param pictureEditRequestMessage 图片编辑请求信息
     * @param session                   会话
     * @param user                      用户
     * @param pictureId                 图片 id
     */
    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage,
                             WebSocketSession session,
                             User user,
                             Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 获取可以生成的位置
        long next = ringBuffer.next();
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     */
    @PreDestroy
    public void close() {
        pictureEditEventDisruptor.shutdown();
    }
}
