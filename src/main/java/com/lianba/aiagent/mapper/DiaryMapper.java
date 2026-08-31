package com.lianba.aiagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lianba.aiagent.model.entity.Diary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 情感日记 Mapper（MyBatis-Plus）
 */
@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {
}
