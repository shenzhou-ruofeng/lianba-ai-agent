package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.model.entity.CommunityPost;
import org.apache.ibatis.annotations.Mapper;

/**
 * 社区帖子 Mapper（MyBatis-Plus）
 */
@Mapper
public interface CommunityPostMapper extends BaseMapper<CommunityPost> {
}
