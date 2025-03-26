package com.eureka.picwavebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eureka.picwavebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.eureka.picwavebackend.model.dto.picture.*;
import com.eureka.picwavebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.model.vo.PictureVO;

import java.util.List;

public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     *
     * @param inputSource          输入源
     * @param pictureUploadRequest 图片上传请求
     * @param loginUser            登录用户
     * @return 图片视图类
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest 图片上传批量请求
     * @param loginUser                   登录用户
     * @return 上传成功图片数量
     */
    Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser);

    /**
     * 删除图片
     *
     * @param pictureId 图片 ID
     * @param loginUser 登录用户
     */
    void deletePicture(long pictureId, User loginUser);

    /**
     * 清除旧图片
     *
     * @param oldPicture 旧图片
     */
    void clearOldPicture(Picture oldPicture);

    /**
     * 编辑图片
     *
     * @param pictureEditRequest 图片编辑请求
     * @param loginUser          请求
     */
    void editPicture(PictureEditRequest pictureEditRequest, User loginUser);

    /**
     * 批量编辑图片
     *
     * @param pictureEditByBatchRequest 图片批量编辑请求
     * @param loginUser                 登录用户
     */
    void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser);

    /**
     * 创建图片扩图任务
     * @param createPictureOutPaintingTaskRequest 创建扩图任务请求
     * @param loginUser 登录用户
     * @return 创建扩图任务响应
     */
    CreateOutPaintingTaskResponse createPictureOutPaintingTask(
            CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest,
            User loginUser);

    /**
     * 构造查询条件
     *
     * @param pictureQueryRequest 图片查询请求
     * @return 查询条件
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 获取图片（包装类）
     *
     * @param picture 图片
     * @return 脱敏图片
     */
    PictureVO getPictureVO(Picture picture);

    /**
     * 获取分页对象（包装类）
     *
     * @param picturePage 图片列表
     * @return 脱敏图片列表
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage);

    /**
     * 根据颜色搜索图片（包装类）
     *
     * @param spaceId   空间 id
     * @param picColor  图片颜色
     * @param loginUser 登录用户
     * @return 脱敏图片列表
     */
    List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser);

    /**
     * 校验图片
     *
     * @param picture 图片
     */
    void validPicture(Picture picture);

    /**
     * 校验图片权限
     *
     * @param loginUser 登录用户
     * @param picture   图片
     */
    void checkPictureAuth(User loginUser, Picture picture);

    /**
     * 补充审核参数
     *
     * @param picture   图片
     * @param loginUser 登录用户
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 审核图片
     *
     * @param pictureReviewRequest 图片审核请求
     * @param loginUser            登录用户
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

}
