package com.lianba.aiagent.controller;

import com.lianba.aiagent.common.BaseResponse;
import com.lianba.aiagent.common.ResultUtils;
import com.lianba.aiagent.model.dto.CreateDiaryDTO;
import com.lianba.aiagent.model.vo.DiaryVO;
import com.lianba.aiagent.model.vo.LoginUserVO;
import com.lianba.aiagent.service.DiaryService;
import com.lianba.aiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 情感日记接口：创建 / 列表 / 详情 / 删除 / AI 分析
 */
@RestController
@RequestMapping("/diary")
public class DiaryController {

    @Resource
    private DiaryService diaryService;

    @Resource
    private UserService userService;

    /**
     * 创建日记（自动触发 AI 情绪分析）
     */
    @PostMapping
    public BaseResponse<DiaryVO> createDiary(@RequestBody CreateDiaryDTO createDTO,
                                              HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        DiaryVO vo = diaryService.createDiary(loginUser.getId(), createDTO.getContent(),
                createDTO.getMood(), createDTO.getTags());
        return ResultUtils.success(vo);
    }

    /**
     * 获取当前用户的日记列表（按时间倒序）
     */
    @GetMapping("/list")
    public BaseResponse<List<DiaryVO>> listDiaries(HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(diaryService.listDiaries(loginUser.getId()));
    }

    /**
     * 获取日记详情
     */
    @GetMapping("/{id}")
    public BaseResponse<DiaryVO> getDiary(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(diaryService.getDiary(loginUser.getId(), id));
    }

    /**
     * 删除日记
     */
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> deleteDiary(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        diaryService.deleteDiary(loginUser.getId(), id);
        return ResultUtils.success(true);
    }

    /**
     * 手动重新触发 AI 情绪分析
     */
    @PostMapping("/{id}/analyze")
    public BaseResponse<DiaryVO> reanalyze(@PathVariable Long id, HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        return ResultUtils.success(diaryService.reanalyze(loginUser.getId(), id));
    }
}
