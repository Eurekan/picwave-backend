package com.eureka.picwavebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.eureka.picwavebackend.model.dto.user.UserQueryRequest;
import com.eureka.picwavebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.eureka.picwavebackend.model.vo.LoginUserVO;
import com.eureka.picwavebackend.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public interface UserService extends IService<User> {
    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账号
     * @param userPassword 用户密码
     * @return 脱敏用户
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户注销
     *
     * @param request 请求
     * @return 是否成功
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取登录用户
     *
     * @param request 请求
     * @return 登录用户
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取脱敏登录用户
     *
     * @param user 用户
     * @return 脱敏用户
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏用户信息
     *
     * @param user 用户
     * @return 脱敏用户信息
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏用户列表
     *
     * @param userList 用户列表
     * @return 脱敏用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 构造查询条件
     *
     * @param userQueryRequest 用户查询请求
     * @return 查询条件
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 获取加密密码
     *
     * @param userPassword 用户密码
     * @return 加密密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 是否为管理员
     * @param user 用户
     * @return 是否
     */
    boolean isAdmin(User user);
}
