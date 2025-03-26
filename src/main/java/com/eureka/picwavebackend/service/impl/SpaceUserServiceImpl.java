package com.eureka.picwavebackend.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.eureka.picwavebackend.exception.ErrorCode;
import com.eureka.picwavebackend.exception.ThrowUtils;
import com.eureka.picwavebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.eureka.picwavebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.eureka.picwavebackend.model.entity.Space;
import com.eureka.picwavebackend.model.entity.SpaceUser;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.model.enums.SpaceRoleEnum;
import com.eureka.picwavebackend.model.vo.SpaceUserVO;
import com.eureka.picwavebackend.model.vo.SpaceVO;
import com.eureka.picwavebackend.model.vo.UserVO;
import com.eureka.picwavebackend.service.SpaceService;
import com.eureka.picwavebackend.service.SpaceUserService;
import com.eureka.picwavebackend.mapper.SpaceUserMapper;
import com.eureka.picwavebackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Eureka
 */
@Service
@RequiredArgsConstructor
public class SpaceUserServiceImpl extends ServiceImpl<SpaceUserMapper, SpaceUser>
        implements SpaceUserService {

    private final SpaceService spaceService;
    private final UserService userService;

    /**
     * 创建空间成员
     *
     * @param spaceUserAddRequest 空间成员添加请求
     * @return 新空间成员 id
     */
    @Override
    public long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest) {
        // 校验参数
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserAddRequest, spaceUser);
        validSpaceUser(spaceUser, true);
        // 新增
        boolean result = this.save(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return spaceUser.getId();
    }

    /**
     * 校验空间成员
     *
     * @param spaceUser 空间用户
     * @param add       是否新增
     */
    @Override
    public void validSpaceUser(SpaceUser spaceUser, boolean add) {
        // 校验参数
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUser.getSpaceId();
        Long userId = spaceUser.getUserId();
        // 新增
        if (add) {
            // 空间 id 和 用户 id 必填
            ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
            User user = userService.getById(userId);
            ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        // 校验空间角色
        String spaceRole = spaceUser.getSpaceRole();
        SpaceRoleEnum spaceRoleEnum = SpaceRoleEnum.getEnumByValue(spaceRole);
        ThrowUtils.throwIf(spaceRole != null && spaceRoleEnum == null,
                ErrorCode.PARAMS_ERROR, "空间角色不存在");
    }

    /**
     * 获取空间成员包装类（单条）
     *
     * @param spaceUser 空间用户
     * @param request   http 请求
     * @return 空间成员包装类
     */
    @Override
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request) {
        // 对象转封装类
        SpaceUserVO spaceUserVO = SpaceUserVO.objToVo(spaceUser);
        // 关联查询用户信息
        Long userId = spaceUser.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceUserVO.setUser(userVO);
        }
        // 关联查询空间信息
        Long spaceId = spaceUser.getSpaceId();
        if (spaceId != null && spaceId > 0) {
            Space space = spaceService.getById(spaceId);
            SpaceVO spaceVO = spaceService.getSpaceVO(space);
            spaceUserVO.setSpace(spaceVO);
        }
        return spaceUserVO;
    }

    /**
     * 获取空间成员包装类（列表）
     *
     * @param spaceUserList 空间用户列表
     * @return 空间用户包装类列表
     */
    @Override
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList) {
        // 1、校验参数
        if (CollectionUtil.isEmpty(spaceUserList)) {
            return new ArrayList<>();
        }
        // 2、对象列表转为包装类列表
        List<SpaceUserVO> spaceUserVOList = spaceUserList
                .stream()
                .map(SpaceUserVO::objToVo)
                .collect(Collectors.toList());
        // 3、关联查询用户信息和空间信息
        // 获取用户 id 和空间 id
        Set<Long> userIdSet = spaceUserList.stream()
                .map(SpaceUser::getUserId)
                .collect(Collectors.toSet());
        Set<Long> spaceIdSet = spaceUserList.stream()
                .map(SpaceUser::getSpaceId)
                .collect(Collectors.toSet());
        // 查询用户信息和空间信息
        Map<Long, User> userIdMap = userService.listByIds(userIdSet)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        Map<Long, Space> spaceIdMap = spaceService.listByIds(spaceIdSet)
                .stream()
                .collect(Collectors.toMap(Space::getId, space -> space));
        // 4、填充用户信息和空间信息
        spaceUserVOList.forEach(spaceUserVO -> {
            Long userId = spaceUserVO.getUserId();
            Long spaceId = spaceUserVO.getSpaceId();
            User user = userIdMap.get(userId);
            spaceUserVO.setUser(user == null ? null : userService.getUserVO(user));
            Space space = spaceIdMap.get(spaceId);
            spaceUserVO.setSpace(space == null ? null : spaceService.getSpaceVO(space));
        });
        return spaceUserVOList;
    }

    /**
     * 获取查询对象
     *
     * @param spaceUserQueryRequest 空间用户查询请求
     * @return 查询对象
     */
    @Override
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest) {
        // 构造查询对象
        QueryWrapper<SpaceUser> queryWrapper = new QueryWrapper<>();
        if (spaceUserQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = spaceUserQueryRequest.getId();
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        String spaceRole = spaceUserQueryRequest.getSpaceRole();
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceRole), "spaceRole", spaceRole);
        return queryWrapper;
    }

}




