package com.eureka.picwavebackend.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@ConfigurationProperties(prefix = "cos.client")
public class CosClientConfig {

    /**
     * 域名
     */
    private String host;

    /**
     * secretId
     */
    private String secretId;

    /**
     * secretKey
     */
    private String secretKey;

    /**
     * 区域
     */
    private String region;

    /**
     * 桶名
     */
    private String bucket;

    @Bean
    public COSClient getCOSClient() {
        // 初始化 secretId secretKey
        COSCredentials cred = new BasicCOSCredentials(this.secretId, this.secretKey);
        // 设置区域
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        // 生成 COS 客户端
        return new COSClient(cred, clientConfig);
    }
}
