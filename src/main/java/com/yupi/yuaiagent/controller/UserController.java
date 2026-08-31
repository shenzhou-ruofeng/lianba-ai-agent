package com.yupi.yuaiagent.controller;

import com.yupi.yuaiagent.common.BaseResponse;
import com.yupi.yuaiagent.common.ResultUtils;
import com.yupi.yuaiagent.model.vo.LoginUserVO;
import com.yupi.yuaiagent.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;

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
    public BaseResponse<String> sendEmailCode(@RequestBody SendCodeRequest sendCodeRequest) {
        String code = userService.sendEmailCode(sendCodeRequest.getEmail());
        return ResultUtils.success(code);
    }

    /**
     * 用户注册（邮箱验证码校验）
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest registerRequest) {
        long userId = userService.userRegister(registerRequest.getEmail(),
                registerRequest.getEmailCode(), registerRequest.getUserPassword(), registerRequest.getCheckPassword());
        return ResultUtils.success(userId);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest loginRequest, HttpServletRequest request) {
        LoginUserVO loginUserVO = userService.userLogin(loginRequest.getUserAccount(),
                loginRequest.getUserPassword(), request);
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
    public BaseResponse<LoginUserVO> updateNickname(@RequestBody UpdateNicknameRequest updateRequest,
                                                    HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUser(request);
        LoginUserVO updated = userService.updateUserNickname(loginUser.getId(), updateRequest.getUserName(), request);
        return ResultUtils.success(updated);
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        return ResultUtils.success(userService.userLogout(request));
    }

    /**
     * 发送验证码请求体
     */
    @Data
    public static class SendCodeRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String email;
    }

    /**
     * 用户注册请求体（邮箱 + 验证码 + 密码）
     */
    @Data
    public static class UserRegisterRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String email;
        private String emailCode;
        private String userPassword;
        private String checkPassword;
    }

    /**
     * 用户登录请求体
     */
    @Data
    public static class UserLoginRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String userAccount;
        private String userPassword;
    }

    /**
     * 更新用户昵称请求体
     */
    @Data
    public static class UpdateNicknameRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String userName;
    }
}
