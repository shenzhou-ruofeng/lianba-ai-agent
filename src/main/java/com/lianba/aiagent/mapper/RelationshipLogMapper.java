package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.model.entity.RelationshipLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 关系状态变更日志 Mapper（MyBatis-Plus）
 */
@Mapper
public interface RelationshipLogMapper extends BaseMapper<RelationshipLog> {
}
