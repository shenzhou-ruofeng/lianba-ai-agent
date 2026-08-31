package com.lianba.aiagent.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianba.aiagent.app.model.LoveReport;
import com.lianba.aiagent.mapper.LoveReportMapper;
import com.lianba.aiagent.model.entity.LoveReportEntity;
import com.lianba.aiagent.model.vo.LoveReportVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 恋爱报告持久化服务：保存/查询恋爱报告
 */
@Slf4j
@Service
public class LoveReportService {

    @Resource
    private LoveReportMapper loveReportMapper;

    /**
     * 保存恋爱报告
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID（可选）
     * @param report    报告内容
     * @return 保存后的报告 VO
     */
    public LoveReportVO saveReport(Long userId, String sessionId, LoveReport report) {
        LoveReportEntity entity = new LoveReportEntity();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setTitle(report.title());
        entity.setSuggestions(JSONUtil.toJsonStr(report.suggestions()));
        entity.setCreateTime(new Date());

        loveReportMapper.insert(entity);
        log.info("恋爱报告已保存: id={}, userId={}, title={}", entity.getId(), userId, report.title());
        return toVO(entity);
    }

    /**
     * 查询用户的恋爱报告列表（按创建时间倒序）
     *
     * @param userId 用户 ID
     * @return 报告列表
     */
    public List<LoveReportVO> listReports(Long userId) {
        LambdaQueryWrapper<LoveReportEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LoveReportEntity::getUserId, userId)
                .orderByDesc(LoveReportEntity::getCreateTime);

        return loveReportMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 查询单个报告
     *
     * @param userId 用户 ID（权限校验）
     * @param id     报告 ID
     * @return 报告 VO
     */
    public LoveReportVO getReport(Long userId, Long id) {
        LoveReportEntity entity = loveReportMapper.selectById(id);
        if (entity == null || !entity.getUserId().equals(userId)) {
            return null;
        }
        return toVO(entity);
    }

    private LoveReportVO toVO(LoveReportEntity entity) {
        LoveReportVO vo = new LoveReportVO();
        vo.setId(entity.getId());
        vo.setSessionId(entity.getSessionId());
        vo.setTitle(entity.getTitle());
        // JSON 数组字符串 -> List<String>
        String suggestionsJson = entity.getSuggestions();
        if (StrUtil.isNotBlank(suggestionsJson)) {
            try {
                JSONArray jsonArray = JSONUtil.parseArray(suggestionsJson);
                vo.setSuggestions(jsonArray.toList(String.class));
            } catch (Exception e) {
                log.warn("解析报告建议 JSON 失败: {}", e.getMessage());
                vo.setSuggestions(List.of(suggestionsJson));
            }
        }
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
