package com.eureka.picwavebackend.service;

import com.eureka.picwavebackend.model.dto.space.analyze.*;
import com.eureka.picwavebackend.model.entity.Space;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.model.vo.space.analyze.SpaceCategoryAnalyzeResponse;
import com.eureka.picwavebackend.model.vo.space.analyze.SpaceSizeAnalyzeResponse;
import com.eureka.picwavebackend.model.vo.space.analyze.SpaceTagAnalyzeResponse;
import com.eureka.picwavebackend.model.vo.space.analyze.SpaceUsageAnalyzeResponse;
import com.eureka.picwavebackend.model.vo.space.analyze.SpaceUserAnalyzeResponse;

import java.util.List;

public interface SpaceAnalyzeService {

    /**
     * 校验空间分析权限
     *
     * @param spaceAnalyzeRequest 空间分析请求
     * @param loginUser           登录用户
     */
    void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser);

    /**
     * 获取空间使用分析数据
     *
     * @param spaceUsageAnalyzeRequest 空间使用分析请求
     * @param loginUser                登录用户
     * @return 空间使用分析结果
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser);

    /**
     * 获取空间分类分析
     *
     * @param spaceCategoryAnalyzeRequest 空间分类分析请求
     * @param loginUser                   登录用户
     * @return 空间分类分析响应
     */
    List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(
            SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest,
            User loginUser);

    /**
     * 获取空间标签分析
     *
     * @param spaceTagAnalyzeRequest 空间标签分析请求
     * @param loginUser              登录用户
     * @return 空间标签分析响应
     */
    List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser);

    /**
     * 获取空间大小分析
     *
     * @param spaceSizeAnalyzeRequest 空间大小分析请求
     * @param loginUser               登录用户
     * @return 空间大小分析响应列表
     */
    List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser);

    /**
     * 用户上传时间分析
     *
     * @param spaceUserAnalyzeRequest 用户上传时间分析请求
     * @param loginUser               登录用户
     * @return 用户上传时间分析响应
     */
    List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser);

    /**
     * 获取空间排行分析
     *
     * @param spaceRankAnalyzeRequest 空间排行分析请求
     * @param loginUser               登录用户
     * @return 空间排行榜
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser);

}
