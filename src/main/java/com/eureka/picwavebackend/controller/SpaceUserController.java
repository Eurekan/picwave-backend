package com.eureka.picwavebackend.controller;

import cn.hutool.core.util.ObjectUtil;
import com.eureka.picwavebackend.common.BaseResponse;
import com.eureka.picwavebackend.common.DeleteRequest;
import com.eureka.picwavebackend.common.ResultUtils;
import com.eureka.picwavebackend.exception.ErrorCode;
import com.eureka.picwavebackend.exception.ThrowUtils;
import com.eureka.picwavebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.eureka.picwavebackend.model.dto.spaceuser.SpaceUserEditRequest;
import com.eureka.picwavebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.eureka.picwavebackend.model.entity.SpaceUser;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.model.vo.SpaceUserVO;
import com.eureka.picwavebackend.service.SpaceUserService;
import com.eureka.picwavebackend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/spaceUser")
public class SpaceUserController {

    private final SpaceUserService spaceUserService;
    private final UserService userService;

    /**
     * 添加空间成员
     *
     * @param spaceUserAddRequest 空间用户添加请求
     * @param request             http 请求
     * @return 新空间成员 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest,
                                           HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);
        long id = spaceUserService.addSpaceUser(spaceUserAddRequest);
        return ResultUtils.success(id);
    }

    /**
     * 删除空间成员
     *
     * @param deleteRequest 删除请求
     * @param request       http 请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest,
                                                 HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        long id = deleteRequest.getId();
        // 判断空间成员是否存在
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceUserService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 编辑空间成员
     *
     * @param spaceUserEditRequest 空间成员编辑请求
     * @param request              http 请求
     * @return 是否编辑成功
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest,
                                               HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(spaceUserEditRequest == null || spaceUserEditRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        // 将实体类和 DTO 进行转换
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserEditRequest, spaceUser);
        // 校验空间成员
        spaceUserService.validSpaceUser(spaceUser, false);
        // 判断空间成员是否存在
        long id = spaceUserEditRequest.getId();
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceUserService.updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 获取某个空间的某个成员信息
     *
     * @param spaceUserQueryRequest 空间成员查询请求
     * @return 空间成员信息
     */
    @PostMapping("/get")
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest,
                                                HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
        // 查询数据库
        SpaceUser spaceUser = spaceUserService.getOne(spaceUserService.getQueryWrapper(spaceUserQueryRequest));
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(spaceUser);
    }

    /**
     * 获取某个空间的所有成员信息
     *
     * @param spaceUserQueryRequest 空间成员查询请求
     * @param request               http 请求
     * @return 空间成员信息列表
     */
    @PostMapping("/list")
    public BaseResponse<List<SpaceUserVO>> getSpaceUserVOList(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest,
                                                              HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }

    /**
     * 获取当前登录用户的所有空间成员信息
     *
     * @param request http 请求
     * @return 空间成员信息列表
     */
    @PostMapping("/list/my")
    public BaseResponse<List<SpaceUserVO>> getMyTeamSpaceList(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        SpaceUserQueryRequest spaceUserQueryRequest = new SpaceUserQueryRequest();
        spaceUserQueryRequest.setUserId(loginUser.getId());
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }

}
