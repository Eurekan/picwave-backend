package com.eureka.picwavebackend.manager.websocket.disruptor;

import com.eureka.picwavebackend.manager.websocket.model.PictureEditRequestMessage;
import com.eureka.picwavebackend.model.entity.User;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 事件生产者
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    @Lazy
    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    /**
     * 发布事件
     *
     * @param pictureEditRequestMessage 图片编辑请求消息
     * @param session                   会话
     * @param user                      用户
     * @param pictureId                 图片 id
     */
    public void publishEvent(
            PictureEditRequestMessage pictureEditRequestMessage,
            WebSocketSession session,
            User user,
            Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 1、获取下一个可用位置
        long next = ringBuffer.next();
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        // 2、设置事件数据
        pictureEditEvent.setSession(session);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 3、发布事件
        ringBuffer.publish(next);
    }

    /**
     * 关闭 disruptor（优雅停机）
     */
    @PreDestroy
    public void close() {
        pictureEditEventDisruptor.shutdown();
    }
}
