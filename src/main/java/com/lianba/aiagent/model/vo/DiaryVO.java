package com.lianba.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 情感日记视图对象（返回给前端）
 */
@Data
public class DiaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 日记内容
     */
    private String content;

    /**
     * 情绪评分（1-5）
     */
    private Integer mood;

    /**
     * 情绪标签文案（如"开心"、"低落"等）
     */
    private String moodLabel;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * AI 情绪分析
     */
    private String aiAnalysis;

    /**
     * 创建时间
     */
    private Date createTime;
}
