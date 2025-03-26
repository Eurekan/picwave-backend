package com.eureka.picwavebackend.api.imagesearch.sub;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.eureka.picwavebackend.exception.BusinessException;
import com.eureka.picwavebackend.exception.ErrorCode;
import com.eureka.picwavebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class GetImagePageUrlApi {

    /**
     * 获取图片页面地址
     *
     * @param imageUrl 图片地址
     * @return 图片页面地址
     */
    public static String getImagePageUrl(String imageUrl) {
        // 1、准备请求参数
        HashMap<String, Object> formData = new HashMap<>();
        formData.put("image", imageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        // 获取当前时间戳
        long uptime = System.currentTimeMillis();
        // 请求地址
        String url = "https://graph.baidu.com/upload?uptime=" + uptime;
        // 2、发送 POST 请求到百度接口
        try (HttpResponse response = HttpUtil.createPost(url)
                .header("acs-token", RandomUtil.randomString(1))
                .form(formData)
                .timeout(5000)
                .execute()) {
            // 判断响应状态
            ThrowUtils.throwIf(HttpStatus.HTTP_OK != response.getStatus(),
                    ErrorCode.OPERATION_ERROR, "接口调用失败");
            // 解析响应
            String responseBody = response.body();
            Map<String, Object> result = JSONUtil.toBean(responseBody, Map.class);
            // 3、处理响应结果
            ThrowUtils.throwIf(result == null || !Integer.valueOf(0).equals(result.get("status")),
                    ErrorCode.OPERATION_ERROR, "接口调用失败");
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            String rawUrl = (String) data.get("url");
            // 解码 URL
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            // 如果 URL 为空
            ThrowUtils.throwIf(searchResultUrl == null, ErrorCode.OPERATION_ERROR, "未返回有效结果");
            return searchResultUrl;
        } catch (Exception e) {
            log.info("搜索失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "搜索失败");
        }
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://www.codefather.cn/logo.png";
        String result = getImagePageUrl(imageUrl);
        System.out.println("搜索成功，结果 URL：" + result);
    }
}
