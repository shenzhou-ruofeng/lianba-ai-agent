package com.lianba.aiagent.controller;

import com.lianba.aiagent.common.BaseResponse;
import com.lianba.aiagent.common.ResultUtils;
import com.lianba.aiagent.model.dto.SendCodeDTO;
import com.lianba.aiagent.model.dto.UpdateNicknameDTO;
import com.lianba.aiagent.model.dto.UpdateUserProfileDTO;
import com.lianba.aiagent.model.dto.UserLoginDTO;
import com.lianba.aiagent.model.dto.UserRegisterDTO;
import com.lianba.aiagent.model.vo.LoginUserVO;
import com.lianba.aiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口：注册 / 登录 / 注销 / 获取当前登录用户
 * <p>
 * 注册采用邮箱验证码校验：先调用发送验证码接口，再用邮箱 + 验证码 + 密码完成注册。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 发送邮箱注册验证码（未配置邮件服务时返回验证码，便于本地开发）
     */
    @PostMapping("/send_code")
    public BaseResponse<String> sendEmailCode(@RequestBody SendCodeDTO sendCodeDTO) {
        String code = userService.sendEmailCode(sendCodeDTO.getEmail());
        return ResultUtils.success(code);
    }

    /**
     * 用户注册（邮箱验证码校验）
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterDTO registerDTO) {
        long userId = userService.userRegister(registerDTO.getEmail(),
                registerDTO.getEmailCode(), registerDTO.getUserPassword(), registerDTO.getCheckPassword());
        return ResultUtils.success(userId);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginDTO loginDTO, HttpServletRequest request) {
        LoginUserVO loginUserVO = userService.userLogin(loginDTO.getUserAccount(),
                loginDTO.getUserPassword(), request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/current")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        return ResultUtils.success(userService.getLoginUser(request));
    }

    /**
     * 更新当前登录用户昵称（保存后立即同步右上角显示，无需重新登录）
     */
    @PostMapping("/update")
    public BaseResponse<LoginUserVO> updateNickname(@RequestBody UpdateNicknameDTO updateDTO,
                                                    HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        LoginUserVO updated = userService.updateUserNickname(loginUser.getId(), updateDTO.getUserName(), request);
        return ResultUtils.success(updated);
    }

    /**
     * 更新用户画像（Onboarding 情感状态选择）
     */
    @PostMapping("/update_profile")
    public BaseResponse<LoginUserVO> updateProfile(@RequestBody UpdateUserProfileDTO updateDTO,
                                                   HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        LoginUserVO updated = userService.updateUserProfile(loginUser.getId(), updateDTO.getRelationshipStatus(), request);
        return ResultUtils.success(updated);
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        return ResultUtils.success(userService.userLogout(request));
    }
}
