package com.eureka.picwavebackend.manager.websocket;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.eureka.picwavebackend.manager.auth.SpaceUserAuthManager;
import com.eureka.picwavebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.eureka.picwavebackend.model.entity.Picture;
import com.eureka.picwavebackend.model.entity.Space;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.model.enums.SpaceTypeEnum;
import com.eureka.picwavebackend.service.PictureService;
import com.eureka.picwavebackend.service.SpaceService;
import com.eureka.picwavebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 拦截器，校验权限并添加会话属性
 */
@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;
    @Autowired
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 握手前拦截
     *
     * @param request    ServerHttpRequest
     * @param response   ServerHttpResponse
     * @param wsHandler  WebSocketHandler
     * @param attributes 会话属性
     * @return 是否放行
     */
    @Override
    public boolean beforeHandshake(@NotNull ServerHttpRequest request,
                                   @NotNull ServerHttpResponse response,
                                   @NotNull WebSocketHandler wsHandler,
                                   @NotNull Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest httpServletRequest = ((ServletServerHttpRequest) request).getServletRequest();
            // 1、校验权限
            // 校验 pictureId
            String pictureId = httpServletRequest.getParameter("pictureId");
            if (StrUtil.isBlank(pictureId)) {
                log.error("缺少图片参数，拒绝握手");
                return false;
            }

            // 校验用户信息
            User loginUser = userService.getLoginUser(httpServletRequest);
            if (ObjectUtil.isEmpty(loginUser)) {
                log.error("用户未登录，拒绝握手");
                return false;
            }

            // 校验图片
            Picture picture = pictureService.getById(pictureId);
            if (picture == null) {
                log.error("图片不存在，拒绝握手");
                return false;
            }
            // 校验空间
            Long spaceId = picture.getSpaceId();
            Space space = null;
            if (spaceId != null) {
                space = spaceService.getById(spaceId);
                if (space == null) {
                    log.error("空间不存在，拒绝握手");
                    return false;
                }
                if (space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()) {
                    log.info("不是团队空间，拒绝握手");
                    return false;
                }
            }
            // 校验权限
            List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
            if (!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)) {
                log.error("用户没有编辑图片的权限，拒绝握手");
                return false;
            }
            // 2、添加会话属性
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
            attributes.put("pictureId", Long.valueOf(pictureId));
        }
        return true;
    }

    @Override
    public void afterHandshake(@NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response, @NotNull WebSocketHandler wsHandler, Exception exception) {

    }
}
