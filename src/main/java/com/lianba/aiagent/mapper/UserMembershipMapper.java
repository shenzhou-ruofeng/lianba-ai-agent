package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.model.entity.UserMembership;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户会员等级 Mapper（MyBatis-Plus）
 */
@Mapper
public interface UserMembershipMapper extends BaseMapper<UserMembership> {
}
