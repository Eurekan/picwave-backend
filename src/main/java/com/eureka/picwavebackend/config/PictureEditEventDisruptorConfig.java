package com.eureka.picwavebackend.config;

import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.eureka.picwavebackend.manager.websocket.disruptor.PictureEditEvent;
import com.eureka.picwavebackend.manager.websocket.disruptor.PictureEditEventWorkHandler;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * 图片编辑事件 disruptor 配置
 */
@Configuration
public class PictureEditEventDisruptorConfig {

    @Resource
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;

    @Bean("pictureEditEventDisruptor")
    public Disruptor<PictureEditEvent> getDisruptor() {
        // ringBuffer 大小
        int bufferSize = 1024 * 256;
        // 1、创建 disruptor 实例
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
                PictureEditEvent::new,
                bufferSize,
                ThreadFactoryBuilder.create().setNamePrefix("pictureEditEventDisruptor").build()
        );
        // 2、关联处理器和事件
        disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);
        // 3、启动 disruptor
        disruptor.start();
        return disruptor;
    }
}
