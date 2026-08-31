package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.model.entity.CommunityComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 社区评论 Mapper（MyBatis-Plus）
 */
@Mapper
public interface CommunityCommentMapper extends BaseMapper<CommunityComment> {
}
