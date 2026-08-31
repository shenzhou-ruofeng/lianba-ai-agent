package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.model.entity.UsageRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用量记录 Mapper（MyBatis-Plus）
 */
@Mapper
public interface UsageRecordMapper extends BaseMapper<UsageRecord> {
}
