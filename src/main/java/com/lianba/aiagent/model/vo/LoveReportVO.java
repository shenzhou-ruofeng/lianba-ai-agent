package com.lianba.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 恋爱报告视图对象（返回给前端）
 */
@Data
public class LoveReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 报告 ID
     */
    private Long id;

    /**
     * 所属会话 ID
     */
    private String sessionId;

    /**
     * 报告标题
     */
    private String title;

    /**
     * 恋爱建议列表
     */
    private List<String> suggestions;

    /**
     * 创建时间
     */
    private Date createTime;
}
