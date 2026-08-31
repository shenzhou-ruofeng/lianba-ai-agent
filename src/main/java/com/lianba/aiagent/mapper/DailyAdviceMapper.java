package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.model.entity.DailyAdvice;
import org.apache.ibatis.annotations.Mapper;

/**
 * 每日情感建议 Mapper（MyBatis-Plus）
 */
@Mapper
public interface DailyAdviceMapper extends BaseMapper<DailyAdvice> {
}
