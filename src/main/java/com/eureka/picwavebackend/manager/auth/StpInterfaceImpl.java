package com.eureka.picwavebackend.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.eureka.picwavebackend.exception.BusinessException;
import com.eureka.picwavebackend.exception.ErrorCode;
import com.eureka.picwavebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.eureka.picwavebackend.model.entity.Picture;
import com.eureka.picwavebackend.model.entity.Space;
import com.eureka.picwavebackend.model.entity.SpaceUser;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.model.enums.SpaceRoleEnum;
import com.eureka.picwavebackend.model.enums.SpaceTypeEnum;
import com.eureka.picwavebackend.service.PictureService;
import com.eureka.picwavebackend.service.SpaceService;
import com.eureka.picwavebackend.service.SpaceUserService;
import com.eureka.picwavebackend.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.eureka.picwavebackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    /**
     * 校验逻辑，返回当前账号所拥有的权限列表
     *
     * @param loginId   登录 id
     * @param loginType 登录类型
     * @return 权限列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 1、校验登录类型
        if (!StpKit.SPACE_TYPE.equals(loginType)) {
            // 返回空权限列表
            return new ArrayList<>();
        }
        // 2、校验登录用户
        // 获取登录用户
        User loginUser = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        // 判空
        if (loginUser == null) {
            // 抛出异常
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户未登录");
        }
        // 3、校验上下文对象字段
        // 3、1 校验是否查询公共图库
        // 获取上下文对象
        SpaceUserAuthContext authContext = getAuthContextByRequest();
        // 获取管理员权限列表
        List<String> ADMIN_PERMISSIONS = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 如果上下文对象所有字段为空，表示公共图库操作
        if (isAllFieldsNull(authContext)) {
            // 返回管理员权限列表
            return ADMIN_PERMISSIONS;
        }
        // 3、2 优先校验 spaceUser 对象
        SpaceUser spaceUser = authContext.getSpaceUser();
        // 私有空间
        if (spaceUser != null) {
            // 返回当前登录用户对应的空间成员角色对应的权限列表
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
        // 获取 userId
        Long userId = loginUser.getId();
        // 获取 spaceUserId
        Long spaceUserId = authContext.getSpaceUserId();
        // 团队空间
        if (spaceUserId != null) {
            // 获取 spaceUser 对象
            spaceUser = spaceUserService.getById(spaceUserId);
            // 判空
            if (spaceUser == null) {
                // 抛出异常
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间用户信息");
            }
            // 获取当前登录用户对应的空间成员对象
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            // 判空
            if (loginSpaceUser == null) {
                // 返回空权限列表
                return new ArrayList<>();
            }
            // 返回当前登录用户对应的空间成员角色对应的权限列表
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        // 3、3 通过 spaceId 或 pictureId 获取 space 对象并处理
        // 获取 spaceId
        Long spaceId = authContext.getSpaceId();
        // 如果 spaceId 为空
        if (spaceId == null) {
            // 通过 pictureId 获取 Picture 对象和 Space 对象
            Long pictureId = authContext.getPictureId();
            // 如果 pictureId 为空
            if (pictureId == null) {
                // 默认通过权限校验
                return ADMIN_PERMISSIONS;
            }
            // 如果 pictureId 不为空，获取 picture 对象
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            // 如果 picture 为空
            if (picture == null) {
                // 抛出异常
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到图片信息");
            }
            // 如果 picture 不为空，获取 spaceId
            spaceId = picture.getSpaceId();
            // 公共图库
            if (spaceId == null) {
                // 判断是否为图片创建者或者管理员
                if (picture.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                    // 返回管理员权限列表
                    return ADMIN_PERMISSIONS;
                } else {
                    // 不是自己的图片，仅可查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        // spaceId 不为空，获取 Space 对象
        Space space = spaceService.getById(spaceId);
        // 判空
        if (space == null) {
            // 抛出异常
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "未找到空间信息");
        }
        // 根据 Space 类型判断权限
        if (space.getSpaceType() == SpaceTypeEnum.PRIVATE.getValue()) {
            // 判断是否是当前登录用户的私有空间
            if (space.getUserId().equals(userId) || userService.isAdmin(loginUser)) {
                // 私有空间，返回管理员权限列表
                return ADMIN_PERMISSIONS;
            } else {
                // 不是自己的空间，返回空权限列表
                return new ArrayList<>();
            }
        } else {
            // 团队空间，查询 SpaceUser 并获取角色和权限
            spaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceId)
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            // 判空
            if (spaceUser == null) {
                // 返回空权限列表
                return new ArrayList<>();
            }
            // 返回当前登录用户对应的空间成员角色对应的权限列表
            return spaceUserAuthManager.getPermissionsByRole(spaceUser.getSpaceRole());
        }
    }

    /**
     * 判断所有字段是否为空（反射）
     *
     * @param object 对象
     * @return 是否为空
     */
    private boolean isAllFieldsNull(Object object) {
        // 对象本身为空
        if (object == null) {
            return true;
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }


    /**
     * 返回一个账号所拥有的角色标识集合 (权限与角色可分开校验)
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    /**
     * 从请求中获取上下文对象
     *
     * @return 上下文对象
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        // 获取当前请求
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes())
                .getRequest();
        // 获取请求头中的 content-type
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;
        // 兼容 GET 和 POST 操作
        if (ContentType.JSON.getValue().equals(contentType)) {
            // POST 操作，读取请求体
            String body = ServletUtil.getBody(request);
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {
            // GET 操作，读取请求参数
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }
        // 根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            // 获取请求 URI
            String requestUri = request.getRequestURI();
            // 去除基础路径
            String partUri = requestUri.replace(contextPath + "/", "");
            // 去除模块名称
            String moduleName = StrUtil.subBefore(partUri, "/", false);
            // 根据模块名称设置对应 id 字段
            switch (moduleName) {
                case "picture":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }

}
