package com.eureka.picwavebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.eureka.picwavebackend.common.DeleteRequest;
import com.eureka.picwavebackend.exception.BusinessException;
import com.eureka.picwavebackend.exception.ErrorCode;
import com.eureka.picwavebackend.exception.ThrowUtils;
import com.eureka.picwavebackend.mapper.PictureMapper;
import com.eureka.picwavebackend.mapper.SpaceMapper;
import com.eureka.picwavebackend.mapper.SpaceUserMapper;
import com.eureka.picwavebackend.model.dto.space.SpaceAddRequest;
import com.eureka.picwavebackend.model.dto.space.SpaceQueryRequest;
import com.eureka.picwavebackend.model.entity.Picture;
import com.eureka.picwavebackend.model.entity.Space;
import com.eureka.picwavebackend.model.entity.SpaceUser;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.model.enums.SpaceLevelEnum;
import com.eureka.picwavebackend.model.enums.SpaceRoleEnum;
import com.eureka.picwavebackend.model.enums.SpaceTypeEnum;
import com.eureka.picwavebackend.model.vo.SpaceVO;
import com.eureka.picwavebackend.model.vo.UserVO;
import com.eureka.picwavebackend.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    private final UserServiceImpl userService;
    private final PictureMapper pictureMapper;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentHashMap<Long, Object> lockMap = new ConcurrentHashMap<>();
    private final SpaceUserMapper spaceUserMapper;

    /**
     * 创建空间
     *
     * @param spaceAddRequest 空间添加请求
     * @param loginUser       登录用户
     * @return 新空间 id
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 将 DTO 转为 实体类
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 填充容量和大小
        this.fillSpaceBySpaceLevel(space);
        Long userId = loginUser.getId();
        space.setUserId(userId);
        // 校验空间
        this.validSpace(space, true);
        // 校验权限（非管理员只能创建普通级别的空间）
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的空间");
        }
        // 针对用户加锁
        lockMap.computeIfAbsent(userId, k -> new Object());
        synchronized (lockMap) {
            try {
                // 操作数据库
                Long newSpaceId = transactionTemplate.execute(status -> {
                    // 判断用户是否已经有私有空间或者团队空间
                    boolean exists = this.lambdaQuery()
                            .eq(Space::getUserId, userId)
                            .eq(Space::getSpaceType, spaceAddRequest.getSpaceType())
                            .exists();
                    ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户每类空间仅能创建一个");
                    // 创建空间
                    boolean save = this.save(space);
                    ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR, "创建空间失败");
                    // 如果是团队空间，关联新增团队成员记录
                    if (spaceAddRequest.getSpaceType() == SpaceTypeEnum.TEAM.getValue()) {
                        SpaceUser spaceUser = new SpaceUser();
                        spaceUser.setSpaceId(space.getId());
                        spaceUser.setUserId(userId);
                        spaceUser.setSpaceRole(SpaceRoleEnum.ADMIN.getValue());
                        int insert = spaceUserMapper.insert(spaceUser);
                        ThrowUtils.throwIf(insert == 0, ErrorCode.OPERATION_ERROR, "创建团队成员记录失败");
                    }
                    // 返回新空间 id
                    return space.getId();
                });
                return Optional.ofNullable(newSpaceId).orElse(-1L);
            } finally {
                // 移除锁
                lockMap.remove(userId);
            }
        }
    }

    /**
     * 删除空间
     *
     * @param deleteRequest 删除请求
     * @param loginUser     登录用户
     */
    @Override
    public void deleteSpace(DeleteRequest deleteRequest, User loginUser) {
        // 校验空间是否存在
        Long spaceId = deleteRequest.getId();
        Space oldSpace = this.getById(spaceId);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        // 校验用户权限（仅管理员和本人可以删除）
        if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库（开启事务）
        transactionTemplate.execute(status -> {
            // 操作数据库
            boolean result = this.removeById(spaceId);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除空间失败");
            // 关联删除空间图片
            List<Long> pictureIdList = pictureMapper.selectList(new QueryWrapper<Picture>()
                            .eq("spaceId", spaceId))
                    .stream()
                    .map(Picture::getId)
                    .collect(Collectors.toList());
            int deleteCount = pictureMapper.deleteByIds(pictureIdList);
            ThrowUtils.throwIf(deleteCount < 0, ErrorCode.OPERATION_ERROR, "删除空间图片失败");
            return true;
        });
    }

    /**
     * 获取空间包装类（单条）
     *
     * @param space 空间
     * @return 空间包装类（单条）
     */
    @Override
    public SpaceVO getSpaceVO(Space space) {
        // 空间转脱敏空间
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    /**
     * 获取空间包装类（分页）
     *
     * @param spacePage 空间分页
     * @return 空间包装类（分页）
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage) {
        List<Space> spaceList = spacePage.getRecords();
        // 1、创建脱敏分页对象
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 2、空间列表转脱敏空间列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // 3、关联查询用户信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 4、填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    /**
     * 构造查询条件
     *
     * @param spaceQueryRequest 空间查询请求
     * @return 查询条件
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        // 构造查询对象
        QueryWrapper<Space> spaceQueryWrapper = new QueryWrapper<>();
        // 参数校验
        if (spaceQueryRequest == null) {
            return spaceQueryWrapper;
        }
        // 取值
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        // 拼接参数
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        spaceQueryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        spaceQueryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        // 拼接排序
        spaceQueryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return spaceQueryWrapper;
    }


    /**
     * 校验空间
     *
     * @param space 空间
     * @param add   是否为创建
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        Integer spaceType = space.getSpaceType();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
            if (spaceLevelEnum == null && spaceTypeEnum == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不存在");
            }
        }
        // 更新
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (spaceType != null && spaceTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间类型不存在");
        }
    }

    /**
     * 校验空间权限
     *
     * @param space     图片
     * @param loginUser 登录用户
     */
    @Override
    public void checkSpaceAuth(Space space, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        ThrowUtils.throwIf(!loginUser.getId().equals(space.getUserId()) || !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
    }

    /**
     * 根据空间等级填充空间信息
     *
     * @param space 空间
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

}




