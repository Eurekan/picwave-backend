package com.eureka.picwavebackend.mapper;

import com.eureka.picwavebackend.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface PictureMapper extends BaseMapper<Picture> {

    @Select("SELECT * FROM picture WHERE `isDelete` = 1")
    List<Picture> selectDeleted();

    @Delete("DELETE FROM picture WHERE `isDelete` = 1")
    int deleteDeleted();


    /**
     * 查询图片分类统计
     * @param spaceId 空间 id （可选），用于用于筛选特定空间的分类统计数据；如果为空，查询所有空间的统计数据
     * @return List -Map<category, value>
     *              -Map<count, value>
     *              -Map<totalSize, value>
     */
    @MapKey("category")
    List<Map<String, Object>> getCategoryStatistics(@Param("spaceId") Long spaceId);
}




