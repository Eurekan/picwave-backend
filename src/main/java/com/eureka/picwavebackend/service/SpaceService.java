package com.eureka.picwavebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eureka.picwavebackend.common.DeleteRequest;
import com.eureka.picwavebackend.model.dto.space.SpaceAddRequest;
import com.eureka.picwavebackend.model.dto.space.SpaceQueryRequest;
import com.eureka.picwavebackend.model.entity.Space;
import com.baomidou.mybatisplus.extension.service.IService;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.model.vo.SpaceVO;

public interface SpaceService extends IService<Space> {

    /**
     * 创建空间
     *
     * @param spaceAddRequest 空间添加请求
     * @param loginUser       登录用户
     * @return 新空间 id
     */
    long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);

    /**
     * 删除空间
     *
     * @param deleteRequest 删除请求
     * @param loginUser     登录用户
     */
    void deleteSpace(DeleteRequest deleteRequest, User loginUser);

    /**
     * 获取空间包装类（单条）
     *
     * @param space 空间
     * @return 空间包装类（单条）
     */
    SpaceVO getSpaceVO(Space space);

    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage 空间分页
     * @return 空间包装类（分页）
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage);

    /**
     * 构造查询条件
     *
     * @param spaceQueryRequest 空间查询请求
     * @return 查询条件
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 根据空间等级填充空间信息
     *
     * @param space 空间
     */
    void fillSpaceBySpaceLevel(Space space);

    /**
     * 校验空间
     *
     * @param space 空间
     * @param add   是否为创建
     */
    void validSpace(Space space, boolean add);

    /**
     * 校验空间权限
     *
     * @param space     图片
     * @param loginUser 登录用户
     */
    void checkSpaceAuth(Space space, User loginUser);
}
