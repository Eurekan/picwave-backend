package com.eureka.picwavebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.eureka.picwavebackend.mapper")
@EnableScheduling
public class PicwaveBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PicwaveBackendApplication.class, args);
    }

}

