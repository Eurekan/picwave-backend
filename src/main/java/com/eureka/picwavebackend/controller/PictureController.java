package com.eureka.picwavebackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eureka.picwavebackend.annotation.AuthCheck;
import com.eureka.picwavebackend.api.aliyunai.AliYunAiApi;
import com.eureka.picwavebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.eureka.picwavebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.eureka.picwavebackend.api.imagesearch.ImageSearchApiFacade;
import com.eureka.picwavebackend.api.imagesearch.model.ImageSearchResult;
import com.eureka.picwavebackend.common.BaseResponse;
import com.eureka.picwavebackend.common.DeleteRequest;
import com.eureka.picwavebackend.common.ResultUtils;
import com.eureka.picwavebackend.constant.UserConstant;
import com.eureka.picwavebackend.exception.BusinessException;
import com.eureka.picwavebackend.exception.ErrorCode;
import com.eureka.picwavebackend.exception.ThrowUtils;
import com.eureka.picwavebackend.model.dto.picture.*;
import com.eureka.picwavebackend.model.entity.Picture;
import com.eureka.picwavebackend.model.entity.PictureTagCategory;
import com.eureka.picwavebackend.model.entity.Space;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.model.enums.PictureReviewStatusEnum;
import com.eureka.picwavebackend.model.vo.PictureVO;
import com.eureka.picwavebackend.service.PictureService;
import com.eureka.picwavebackend.service.SpaceService;
import com.eureka.picwavebackend.service.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/picture")
public class PictureController {

    private final UserService userService;
    private final PictureService pictureService;
    private final StringRedisTemplate stringRedisTemplate;
    private final SpaceService spaceService;
    private final AliYunAiApi aliYunAiApi;

    /**
     * 上传图片
     *
     * @param multipartFile        文件
     * @param pictureUploadRequest 图片上传请求
     * @param request              请求
     * @return 脱敏图片
     */
    @PostMapping("/upload")
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 通过 URL 上传图片
     *
     * @param pictureUploadRequest 图片上传请求
     * @param request              请求
     * @return 脱敏图片
     */
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String fileUrl = pictureUploadRequest.getFileUrl();
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 批量抓取和创建图片（仅管理员可用）
     *
     * @param pictureUploadByBatchRequest 图片上传批量请求
     * @param request                     请求
     * @return 上传成功图片数量
     */
    @PostMapping("upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Integer> uploadBatch(@RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Integer uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }

    /**
     * 删除图片
     *
     * @param deleteRequest 删除请求
     * @return 是否成功
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        pictureService.deletePicture(deleteRequest.getId(), userService.getLoginUser(request));
        return ResultUtils.success(true);
    }

    /**
     * 更新图片（仅管理员使用）
     *
     * @param pictureUpdateRequest 图片更新请求
     * @return 是否成功
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,
                                               HttpServletRequest request) {
        // 1、校验参数
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2、校验图片
        Long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        pictureService.validPicture(picture);

        // 3、补充审核参数
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(picture, loginUser);

        // 4、更新
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 编辑图片
     *
     * @param pictureEditRequest 图片编辑请求
     * @return 是否成功
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditRequest == null || pictureEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPicture(pictureEditRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest 图片批量编辑请求
     * @param request                   请求
     * @return 是否成功
     */
    @PostMapping("/edit/batch")
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest,
                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 创建扩图任务
     *
     * @param createPictureOutPaintingTaskRequest 创建扩图任务请求
     * @param request                             请求
     * @return 创建扩图任务响应
     */
    @PostMapping("/out_painting/create_task")
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(
            @RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(
                createPictureOutPaintingTaskRequest == null ||
                        createPictureOutPaintingTaskRequest.getPictureId() == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse response = pictureService
                .createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(response);
    }

    /**
     * 查询扩图任务
     *
     * @param taskId 任务 id
     * @return 获取扩图任务响应
     */
    @GetMapping("/out_painting/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), ErrorCode.PARAMS_ERROR);
        GetOutPaintingTaskResponse response = aliYunAiApi.getOutPaintingTask(taskId);
        return ResultUtils.success(response);
    }

    /**
     * 审核图片（仅管理员可用）
     *
     * @param pictureReviewRequest 图片审核请求
     * @param request              请求
     * @return 是否成功
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图片（仅管理员可用）
     *
     * @param id 图片 id
     * @return 图片
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片（封装类）
     *
     * @param id 图片 id
     * @return 图片
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验空间权限
        if (picture.getSpaceId() != null) {
            User loginUser = userService.getLoginUser(request);
            pictureService.checkPictureAuth(loginUser, picture);
        }
        return ResultUtils.success(pictureService.getPictureVO(picture));
    }

    /**
     * 分页获取图品列表（仅管理员可用）
     *
     * @param pictureQueryRequest 图片查询请求
     * @return 分页对象
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> getPictureListByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取图品列表（封装类）
     *
     * @param pictureQueryRequest 图片查询请求
     * @return 分页对象
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> getPictureVOListByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                                HttpServletRequest request) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);
        // 校验空间权限
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 查询公共图库
            pictureQueryRequest.setNullSpaceId(true);
            // 查询过审
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        } else {
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            ThrowUtils.throwIf(!Objects.equals(space.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "没有空间权限");
        }
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage));
    }

    /**
     * 初始化本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(1024)
            .maximumSize(10000L)
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();

    /**
     * 分页获取图品列表（封装类，多级缓存）
     *
     * @param pictureQueryRequest 图片查询请求
     * @return 分页对象
     */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> getPictureVOListByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest) {
        int current = pictureQueryRequest.getCurrent();
        int pageSize = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR);
        // 查询过审
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 构建缓存键
        String jsonStr = JSONUtil.toJsonStr(pictureQueryRequest);
        String encryptKey = DigestUtils.md5DigestAsHex(jsonStr.getBytes());
        String caffeineKey = "getPictureVOListByPage:" + encryptKey;
        String redisKey = "picwave:getPictureVOListByPageWithCache:" + encryptKey;
        // 查询本地缓存
        String caffeineValue = LOCAL_CACHE.getIfPresent(caffeineKey);
        if (caffeineValue != null) {
            // 命中，直接返回
            Page<PictureVO> cachePage = JSONUtil.toBean(caffeineValue, Page.class);
            return ResultUtils.success(cachePage);
        }
        // 未命中，查询 Redis 缓存
        String redisValue = stringRedisTemplate.opsForValue().get(redisKey);
        if (redisValue != null) {
            // 命中，直接返回
            Page<PictureVO> cachePage = JSONUtil.toBean(redisValue, Page.class);
            return ResultUtils.success(cachePage);
        }
        // 未命中，查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize),
                pictureService.getQueryWrapper(pictureQueryRequest));
        // 获取脱敏分页对象
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage);
        // 写入本地缓存
        LOCAL_CACHE.put(caffeineKey, JSONUtil.toJsonStr(pictureVOPage));
        // 写入 Redis 缓存
        stringRedisTemplate.opsForValue().set(redisKey, JSONUtil.toJsonStr(pictureVOPage), RandomUtil.randomInt(5, 10), TimeUnit.MINUTES);
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 获取图片标签和分类
     *
     * @return 图片标签分类
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> getPictureTagCategoryList() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

    /**
     * 根据颜色搜索图片
     *
     * @param searchPictureByColorRequest 根据颜色搜索图片请求
     * @param request                     请求
     * @return pictureVOList
     */
    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest,
                                                              HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        String picColor = searchPictureByColorRequest.getPicColor();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOList = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(pictureVOList);
    }

    /**
     * 以图识图
     *
     * @param searchPictureByPictureRequest 以图搜图请求
     * @return imageSearchResultList
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null, ErrorCode.PARAMS_ERROR);
        Picture oldPicture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 将 webp 格式不能搜索问题（调用 cos 方法转为 png 格式）
        String url = oldPicture.getUrl() + "?imageMogr2/format/png";
        List<ImageSearchResult> resultList = ImageSearchApiFacade.searchImage(url);
        return ResultUtils.success(resultList);
    }
}
