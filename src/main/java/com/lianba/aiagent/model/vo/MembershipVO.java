package com.lianba.aiagent.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 会员等级信息 VO
 */
@Data
public class MembershipVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前等级：bronze / silver / gold / diamond
     */
    private String tier;

    /**
     * 等级中文名
     */
    private String tierLabel;

    /**
     * 等级图标 emoji
     */
    private String tierIcon;

    /**
     * 下一等级名称（已达最高则为 null）
     */
    private String nextTier;

    /**
     * 升级进度百分比（0-100，已达最高则为 100）
     */
    private int progress;

    /**
     * 累计对话次数
     */
    private long totalChats;

    /**
     * 累计日记数
     */
    private long totalDiaries;

    /**
     * 累计报告数
     */
    private long totalReports;

    /**
     * 今日对话次数
     */
    private long todayChats;

    /**
     * 今日日记数
     */
    private long todayDiaries;
}
