package com.eureka.picwavebackend.job;

import cn.hutool.core.util.StrUtil;
import com.eureka.picwavebackend.manager.CosManager;
import com.eureka.picwavebackend.mapper.PictureMapper;
import com.eureka.picwavebackend.model.entity.Picture;
import com.eureka.picwavebackend.config.CosClientConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PictureCleanupTask {

    private final PictureMapper pictureMapper;
    private final CosManager cosManager;
    private final CosClientConfig cosClientConfig;

    /**
     * 每天凌晨 0 点执行清理任务
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupDeletedPictures() {
        log.info("开始清理已删除的图片...");
        // 查询 isDelete 为 1 的图片
        List<Picture> deletedPictures = pictureMapper.selectDeleted();
        if (deletedPictures.isEmpty()) {
            log.info("没有需要清理的图片。");
            return;
        }
        List<String> keysToDelete = new ArrayList<>();
        for (Picture picture : deletedPictures) {
            // 删除 COS 上的图片
            String url = picture.getUrl();
            String thumbnailUrl = picture.getThumbnailUrl();
            if (StrUtil.isNotBlank(url)) {
                String key = url.replace(cosClientConfig.getHost() + "/", "");
                keysToDelete.add(key);
                log.info("准备删除 COS 上的图片, key = {}", key);
            }
            if (StrUtil.isNotBlank(thumbnailUrl)) {
                String thumbnailKey = thumbnailUrl.replace(cosClientConfig.getHost() + "/", "");
                keysToDelete.add(thumbnailKey);
                log.info("准备删除 COS 上的缩略图, thumbnailKey = {}", thumbnailKey);
            }
        }
        if (!keysToDelete.isEmpty()) {
            try {
                cosManager.deleteObjects(keysToDelete);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            log.info("删除 COS 上的图片成功, 数量 = {}", keysToDelete.size());
        }
        // 物理删除数据库中的记录
        int deletedCount = pictureMapper.deleteDeleted();
        log.info("物理删除数据库中的图片记录成功, 数量 = {}", deletedCount);
    }
}

