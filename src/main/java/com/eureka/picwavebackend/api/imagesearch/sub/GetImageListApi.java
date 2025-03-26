package com.eureka.picwavebackend.api.imagesearch.sub;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.eureka.picwavebackend.api.imagesearch.model.ImageSearchResult;
import com.eureka.picwavebackend.exception.BusinessException;
import com.eureka.picwavebackend.exception.ErrorCode;
import com.eureka.picwavebackend.exception.ThrowUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class GetImageListApi {

    /**
     * 获取图片列表
     *
     * @param url 图片列表页面地址
     * @return 图片列表
     */
    public static List<ImageSearchResult> getImageList(String url) {
        try (HttpResponse response = HttpUtil.createGet(url).execute()) {
            // 获取响应状态码和响应体
            int statusCode = response.getStatus();
            String body = response.body();
            // 处理响应状态码和响应体
            if (statusCode == HttpStatus.HTTP_OK) {
                // 解析 JSON 数据并处理
                return processResponse(body);
            } else {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
        } catch (Exception e) {
            log.error("获取图片列表失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片列表失败");
        }
    }

    /**
     * 处理接口响应内容响应
     *
     * @param responseBody 接口响应内容
     * @return imageList
     */
    private static List<ImageSearchResult> processResponse(String responseBody) {
        // 解析响应对象
        JSONObject jsonObject = new JSONObject(responseBody);
        ThrowUtils.throwIf(!jsonObject.containsKey("data"), ErrorCode.OPERATION_ERROR, "未能获取到图片列表");
        JSONObject data = jsonObject.getJSONObject("data");
        ThrowUtils.throwIf(!data.containsKey("list"), ErrorCode.OPERATION_ERROR, "未能获取到图片列表");
        JSONArray list = data.getJSONArray("list");
        return JSONUtil.toList(list, ImageSearchResult.class);
    }

    public static void main(String[] args) {
        String url = "https://graph.baidu.com/ajax/pcsimi?carousel=503&entrance=GENERAL&extUiData%5BisLogoShow%5D=1&inspire=general_pc&limit=30&next=2&render_type=card&session_id=13902509851390411901&sign=12116325d996dbfccefee01742654652&tk=ccdbb&tpl_from=pc";
        List<ImageSearchResult> imageList = getImageList(url);
        System.out.println("搜索成功" + imageList);
    }
}
