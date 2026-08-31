package com.lianba.aiagent.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建情感日记请求 DTO
 */
@Data
public class CreateDiaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日记内容（必填）
     */
    private String content;

    /**
     * 情绪评分（1-5，可选）
     */
    private Integer mood;

    /**
     * 标签列表（可选）
     */
    private List<String> tags;
}
