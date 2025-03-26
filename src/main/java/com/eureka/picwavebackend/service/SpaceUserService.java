package com.eureka.picwavebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.eureka.picwavebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.eureka.picwavebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.eureka.picwavebackend.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.eureka.picwavebackend.model.vo.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author Eureka
 */
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 创建空间成员
     *
     * @param spaceUserAddRequest 空间成员添加请求
     * @return 新空间成员 id
     */
    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    /**
     * 校验空间成员
     *
     * @param spaceUser 空间用户
     * @param add       是否新增
     */
    void validSpaceUser(SpaceUser spaceUser, boolean add);

    /**
     * 获取空间成员包装类（单条）
     *
     * @param spaceUser 空间用户
     * @param request   http 请求
     * @return 空间成员包装类
     */
    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 获取空间成员包装类（列表）
     *
     * @param spaceUserList 空间用户列表
     * @return 空间用户包装类列表
     */
    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 获取查询对象
     *
     * @param spaceUserQueryRequest 空间用户查询请求
     * @return 查询对象
     */
    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);
}
