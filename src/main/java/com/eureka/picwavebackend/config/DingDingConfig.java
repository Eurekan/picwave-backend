package com.eureka.picwavebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "dingding")
public class DingDingConfig {

    private String token;
    private String userId;
    private String secret;
}
