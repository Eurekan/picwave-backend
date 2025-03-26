package com.eureka.picwavebackend.service.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.eureka.picwavebackend.exception.BusinessException;
import com.eureka.picwavebackend.exception.ErrorCode;
import com.eureka.picwavebackend.exception.ThrowUtils;
import com.eureka.picwavebackend.model.dto.space.analyze.*;
import com.eureka.picwavebackend.model.entity.Picture;
import com.eureka.picwavebackend.model.entity.Space;
import com.eureka.picwavebackend.model.entity.User;
import com.eureka.picwavebackend.model.vo.space.analyze.*;
import com.eureka.picwavebackend.service.PictureService;
import com.eureka.picwavebackend.service.SpaceAnalyzeService;
import com.eureka.picwavebackend.service.SpaceService;
import com.eureka.picwavebackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpaceAnalyzeServiceImpl implements SpaceAnalyzeService {

    private final UserService userService;
    private final SpaceService spaceService;
    private final PictureService pictureService;

    /**
     * 校验空间分析权限
     *
     * @param spaceAnalyzeRequest 空间分析请求
     * @param loginUser           登录用户
     */
    @Override
    public void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser) {
        if (spaceAnalyzeRequest.isQueryPublic() || spaceAnalyzeRequest.isQueryAll()) {
            ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权限查询公共空间");
        }
        if (Objects.nonNull(spaceAnalyzeRequest.getSpaceId()) && spaceAnalyzeRequest.getSpaceId() > 0L) {
            Space space = spaceService.getById(spaceAnalyzeRequest.getSpaceId());
            ThrowUtils.throwIf(!userService.isAdmin(loginUser) || !space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限查询指定空间");
        }
    }

    /**
     * 获取空间使用分析数据
     *
     * @param spaceUsageAnalyzeRequest 空间使用分析请求
     * @param loginUser                登录用户
     * @return 空间使用分析结果
     */
    @Override
    public SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceUsageAnalyzeRequest spaceUsageAnalyzeRequest, User loginUser) {
        // 1、校验空间分析权限
        checkSpaceAnalyzeAuth(spaceUsageAnalyzeRequest, loginUser);
        // 2、构造查询条件
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        // 根据空间分析请求构造查询条件
        fillAnalyzeQueryWrapper(spaceUsageAnalyzeRequest, pictureQueryWrapper);
        pictureQueryWrapper.select("picSize");
        // 3、查询
        List<Object> pictureObjList = pictureService.getBaseMapper().selectObjs(pictureQueryWrapper);
        // 获取已使用数量
        long usedCount = pictureObjList.size();
        // 获取已使用大小
        long usedSize = pictureObjList.stream()
                .filter(Objects::nonNull)
                .mapToLong(result -> result instanceof Long ? (Long) result : 0)
                .sum();
        // 4、公共图库
        if (spaceUsageAnalyzeRequest.isQueryPublic() || spaceUsageAnalyzeRequest.isQueryAll()) {
            return SpaceUsageAnalyzeResponse.builder()
                    .usedCount(usedCount)
                    .usedSize(usedSize)
                    .build();
        }
        // 5、私有空间
        if (Objects.nonNull(spaceUsageAnalyzeRequest.getSpaceId()) && spaceUsageAnalyzeRequest.getSpaceId() > 0L) {
            Long spaceId = spaceUsageAnalyzeRequest.getSpaceId();
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验空间权限
            spaceService.checkSpaceAuth(space, loginUser);
            // 构造空间使用分析响应
            double sizeUsageRatio = NumberUtil.round(space.getTotalSize() * 100.0 / space.getMaxSize(), 2).doubleValue();
            double countUsageRatio = NumberUtil.round(space.getTotalCount() * 100.0 / space.getMaxCount(), 2).doubleValue();
            return SpaceUsageAnalyzeResponse.builder()
                    .usedCount(usedCount)
                    .maxCount(space.getMaxCount())
                    .countUsageRatio(countUsageRatio)
                    .usedSize(usedSize)
                    .maxSize(space.getMaxSize())
                    .sizeUsageRatio(sizeUsageRatio)
                    .build();
        }
        return new SpaceUsageAnalyzeResponse();
    }

    /**
     * 获取空间分类分析
     *
     * @param spaceCategoryAnalyzeRequest 空间分类分析请求
     * @param loginUser                   登录用户
     * @return 空间分类分析响应
     */
    @Override
    public List<SpaceCategoryAnalyzeResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyzeRequest spaceCategoryAnalyzeRequest, User loginUser) {
        // 1、校验参数
        ThrowUtils.throwIf(spaceCategoryAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 2、校验权限
        checkSpaceAnalyzeAuth(spaceCategoryAnalyzeRequest, loginUser);
        // 3、构造查询条件
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceCategoryAnalyzeRequest, pictureQueryWrapper);
        // 使用 Mybatis-Plus 分组查询
        pictureQueryWrapper.select("category AS category",
                        "COUNT(*) AS count",
                        "SUM(picSize) AS totalSize")
                .groupBy("category");
        // 4、查询并转换结果
        return pictureService.getBaseMapper().selectMaps(pictureQueryWrapper)
                .stream()
                .map(result -> {
                    String category = result.get("category") != null ? result.get("category").toString() : "未分类";
                    Long count = ((Number) result.get("count")).longValue();
                    Long totalSize = ((Number) result.get("totalSize")).longValue();
                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                })
                .collect(Collectors.toList());
        /*// 4、使用 Mapper 分组查询
        List<Map<String, Object>> categoryStatistics =
                pictureMapper.getCategoryStatistics(spaceCategoryAnalyzeRequest.getSpaceId());
        // 5、转换结果
        return categoryStatistics.stream()
                .map(map -> {
                    Long count = ((Number) map.get("count")).longValue();
                    Long totalSize = ((Number) map.get("totalSize")).longValue();
                    String category = map.get("category") instanceof String ? (String) map.get("category") : "未分类";
                    return new SpaceCategoryAnalyzeResponse(category, count, totalSize);
                })
                .collect(Collectors.toList());*/
    }

    /**
     * 获取空间标签分析
     *
     * @param spaceTagAnalyzeRequest 空间标签分析请求
     * @param loginUser              登录用户
     * @return 空间标签分析响应
     */
    @Override
    public List<SpaceTagAnalyzeResponse> getSpaceTagAnalyze(
            SpaceTagAnalyzeRequest spaceTagAnalyzeRequest, User loginUser) {
        // 1、校验参数
        ThrowUtils.throwIf(spaceTagAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 2、校验权限
        checkSpaceAnalyzeAuth(spaceTagAnalyzeRequest, loginUser);
        // 3、构造查询条件
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceTagAnalyzeRequest, pictureQueryWrapper);
        // 4、查询所有符合条件的标签
        pictureQueryWrapper.select("tags");
        List<String> tagsJsonList = pictureService.getBaseMapper().selectObjs(pictureQueryWrapper)
                .stream()
                .filter(Objects::nonNull)
                .map(Objects::toString)
                .collect(Collectors.toList());
        // 5、合并所有标签并统计使用次数
        Map<String, Long> tagCountMap = tagsJsonList.stream()
                .flatMap(tagsJson -> JSONUtil.toList(tagsJson, String.class).stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));
        // 6、转换为响应对象，按使用次数降序排序
        return tagCountMap.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue())) // 降序排列
                .map(entry -> new SpaceTagAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 获取空间大小分析
     *
     * @param spaceSizeAnalyzeRequest 空间大小分析请求
     * @param loginUser               登录用户
     * @return 空间大小分析响应列表
     */
    @Override
    public List<SpaceSizeAnalyzeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyzeRequest spaceSizeAnalyzeRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(spaceSizeAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 校验空间限
        checkSpaceAnalyzeAuth(spaceSizeAnalyzeRequest, loginUser);
        // 构造查询条件
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        fillAnalyzeQueryWrapper(spaceSizeAnalyzeRequest, pictureQueryWrapper);
        // 构造查询条件
        pictureQueryWrapper.select("picSize");
        // 定义分段范围，注意使用有序 Map
        Map<String, Long> sizeRanges = new LinkedHashMap<>();
        pictureService.getBaseMapper().selectObjs(pictureQueryWrapper)
                .stream()
                .map(size -> ((Number) size).longValue())
                .forEach(picSize -> {
                    if (picSize < 100 * 1024) {
                        sizeRanges.put("<100KB", sizeRanges.getOrDefault("<100KB", 0L) + 1);
                    } else if (picSize < 500 * 1024) {
                        sizeRanges.put("100KB-500KB", sizeRanges.getOrDefault("100KB-500KB", 0L) + 1);
                    } else if (picSize < 1024 * 1024) {
                        sizeRanges.put("500KB-1MB", sizeRanges.getOrDefault("500KB-1MB", 0L) + 1);
                    } else {
                        sizeRanges.put(">1MB", sizeRanges.getOrDefault(">1MB", 0L) + 1);
                    }
                });
        // 转换为响应对象
        return sizeRanges.entrySet().stream()
                .map(entry -> new SpaceSizeAnalyzeResponse(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 用户上传时间分析
     *
     * @param spaceUserAnalyzeRequest 用户上传时间分析请求
     * @param loginUser               登录用户
     * @return 用户上传时间分析响应
     */
    @Override
    public List<SpaceUserAnalyzeResponse> getSpaceUserAnalyze(SpaceUserAnalyzeRequest spaceUserAnalyzeRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(spaceUserAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 校验权限
        checkSpaceAnalyzeAuth(spaceUserAnalyzeRequest, loginUser);
        // 构造查询条件
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        Long userId = spaceUserAnalyzeRequest.getUserId();
        pictureQueryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        fillAnalyzeQueryWrapper(spaceUserAnalyzeRequest, pictureQueryWrapper);
        // 统计用户上传时间
        // 分析维度：每日、每周、每月
        String timeDimension = spaceUserAnalyzeRequest.getTimeDimension();
        switch (timeDimension) {
            case "day":
                pictureQueryWrapper.select("DATE_FORMAT(createTime, '%Y-%m-%d') AS period", "COUNT(*) AS count");
                break;
            case "week":
                pictureQueryWrapper.select("YEARWEEK(createTime) AS period", "COUNT(*) AS count");
                break;
            case "month":
                pictureQueryWrapper.select("DATE_FORMAT(createTime, '%Y-%m') AS period", "COUNT(*) AS count");
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的时间维度");
        }
        // 分组和排序
        pictureQueryWrapper.groupBy("period").orderByAsc("period");

        // 查询结果并转换
        List<Map<String, Object>> queryResult = pictureService.getBaseMapper().selectMaps(pictureQueryWrapper);
        return queryResult.stream()
                .map(result -> {
                    String period = result.get("period").toString();
                    Long count = ((Number) result.get("count")).longValue();
                    return new SpaceUserAnalyzeResponse(period, count);
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取空间排行分析
     *
     * @param spaceRankAnalyzeRequest 空间排行分析请求
     * @param loginUser               登录用户
     * @return 空间排行榜
     */
    @Override
    public List<Space> getSpaceRankAnalyze(SpaceRankAnalyzeRequest spaceRankAnalyzeRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(spaceRankAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        // 校验空间权限
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看空间排行");
        // 构造查询条件
        QueryWrapper<Space> spaceQueryWrapper = new QueryWrapper<>();
        spaceQueryWrapper.select("id", "spaceName", "userId", "totalSize")
                .orderByDesc("totalSize")
                .last("LIMIT " + spaceRankAnalyzeRequest.getTopN());
        // 查询结果
        return spaceService.list(spaceQueryWrapper);
    }

    /**
     * 填充 QueryWrapper 对应属性
     *
     * @param spaceAnalyzeRequest 空间分析请求类
     * @param queryWrapper        查询对象
     */
    private static void fillAnalyzeQueryWrapper(SpaceAnalyzeRequest spaceAnalyzeRequest,
                                                QueryWrapper<Picture> queryWrapper) {
        if (spaceAnalyzeRequest.isQueryAll()) {
            return;
        }
        if (spaceAnalyzeRequest.isQueryPublic()) {
            queryWrapper.isNull("spaceId");
            return;
        }
        Long spaceId = spaceAnalyzeRequest.getSpaceId();
        if (spaceId != null) {
            queryWrapper.eq("spaceId", spaceId);
            return;
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "未指定查询范围");
    }
}
