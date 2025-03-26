package com.eureka.picwavebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.eureka.picwavebackend.config.CosClientConfig;
import com.eureka.picwavebackend.exception.BusinessException;
import com.eureka.picwavebackend.exception.ErrorCode;
import com.eureka.picwavebackend.manager.CosManager;
import com.eureka.picwavebackend.model.dto.file.UploadPictureResult;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.COSObjectInputStream;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * 图片上传模板
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    protected CosManager cosManager;

    @Resource
    protected CosClientConfig cosClientConfig;

    @Resource
    protected COSClient cosClient;

    /**
     * 模板方法
     *
     * @param inputSource      输入源
     * @param uploadPathPrefix 上传路径前缀
     * @return UploadPictureResult
     */
    public UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1、校验图片
        validPicture(inputSource);
        // 2、生成上传地址
        String uuid = RandomUtil.randomString(16);
        String originFilename = getOriginFilename(inputSource);
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);

        File file = null;
        try {
            // 3、创建临时文件
            file = File.createTempFile(uploadPath, null);
            // 4、处理文件
            processFile(inputSource, file);
            // 5、上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            // 获取图片信息
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 获取图片处理信息
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();
            if (CollUtil.isNotEmpty(objectList)) {
                CIObject compressedCiObject = objectList.get(0);
                CIObject thumbnailCiObject = compressedCiObject;
                if (objectList.size() > 1) {
                    thumbnailCiObject = objectList.get(1);
                }
                // 封装返回结果（处理信息）
                return buildResult(originFilename, compressedCiObject, thumbnailCiObject);
            }
            // 6、封装返回结果（原图信息）
            return buildResult(originFilename, file, uploadPath, imageInfo);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 7、删除临时文件
            deleteTempFile(file);
            // 8、删除原图
            cosManager.deleteObject(uploadPath);
            log.info("删除原图成功");
        }
    }

    /**
     * 校验输入源（本地文件或 URL）
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 封装返回结果（原图信息）
     *
     * @param originFilename 原始文件名
     * @param file           文件
     * @param uploadPath     上传路径
     * @param imageInfo      图片信息
     * @return uploadPictureResult
     */
    private UploadPictureResult buildResult(String originFilename, File file, String uploadPath, ImageInfo imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());
        uploadPictureResult.setPicSize(FileUtil.size(file));
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);
        uploadPictureResult.setPicColor(this.getImageAve(uploadPath));
        return uploadPictureResult;
    }

    /**
     * 封装返回结果（处理信息）
     *
     * @param originFilename     原始文件名
     * @param compressedCiObject 压缩处理后的图片对象
     * @return uploadPictureResult
     */
    private UploadPictureResult buildResult(String originFilename, CIObject compressedCiObject, CIObject thumbnailCiObject) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());
        uploadPictureResult.setPicColor(this.getImageAve(compressedCiObject.getKey()));
        // 设置压缩图地址和缩略图地址
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());
        return uploadPictureResult;
    }

    /**
     * 获取图片主色调
     *
     * @param key 唯一值
     * @return 图片主色调
     */
    public String getImageAve(String key) {
        // 创建获取对象请求
        GetObjectRequest getObj = new GetObjectRequest(cosClientConfig.getBucket(), key);
        // 设置图片处理规则为获取图片主色调
        String rule = "imageAve";
        getObj.putCustomQueryParameter(rule, null);
        // 获取对象
        COSObject object = cosClient.getObject(getObj);
        // 读取内容流并解析主色调信息
        try (COSObjectInputStream objectContent = object.getObjectContent();
             ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            // 读取内容流
            byte[] buffer = new byte[1024];
            int length;
            while ((length = objectContent.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            // 将字节数组转换为字符串
            String aveColor = result.toString("UTF-8");
            return JSONUtil.parseObj(aveColor).getStr("RGB");
        } catch (IOException e) {
            log.error("获取图片主色调失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "获取图片主色调失败");
        }
    }


    /**
     * 删除临时文件
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }
}
