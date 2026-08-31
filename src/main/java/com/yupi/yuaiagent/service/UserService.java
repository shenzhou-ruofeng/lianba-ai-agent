package com.yupi.yuaiagent.service;

import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.yupi.yuaiagent.constant.UserConstant;
import com.yupi.yuaiagent.exception.BusinessException;
import com.yupi.yuaiagent.exception.ErrorCode;
import com.yupi.yuaiagent.exception.ThrowUtils;
import com.yupi.yuaiagent.model.entity.User;
import com.yupi.yuaiagent.model.vo.LoginUserVO;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户服务：注册 / 登录 / 注销 / 获取当前登录用户
 * <p>
 * 注册采用邮箱验证码校验：验证码发送到用户邮箱（未配置邮件服务时降级为接口直接返回验证码，便于本地开发），
 * 注册账号即用户邮箱。存储策略与项目其他模块保持一致：配置了数据源（prod profile）时使用 ai_user 表持久化，
 * 未配置数据源（本地开发）时优雅降级为内存存储，保证登录功能开箱可用。
 */
@Slf4j
@Service
public class UserService {

    /**
     * 密码加密盐值
     */
    private static final String SALT = "yu_ai_agent";

    /**
     * 邮箱格式校验正则
     */
    private static final String EMAIL_REGEX = "^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$";

    /**
     * 验证码有效期（5 分钟）
     */
    private static final long CODE_EXPIRE_MILLIS = 5 * 60 * 1000;

    /**
     * 验证码重发间隔（60 秒）
     */
    private static final long RESEND_INTERVAL_MILLIS = 60 * 1000;

    /**
     * 验证码存储：邮箱 -> 验证码信息（重启后失效）
     */
    private static final Map<String, EmailCodeInfo> EMAIL_CODE_STORE = new ConcurrentHashMap<>();

    /**
     * 邮件发件人地址（与 spring.mail.username 一致），未配置邮件服务时为空
     */
    @Value("${spring.mail.username:}")
    private String mailFromAddress;

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    /**
     * 数据库不可用时的内存降级存储：账号 -> 用户
     */
    private final Map<String, User> memoryUserStore = new ConcurrentHashMap<>();

    private final AtomicLong memoryIdGenerator = new AtomicLong(1);

    private final JdbcTemplate jdbcTemplate;

    private volatile boolean databaseAvailable = false;

    public UserService(ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                       ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * 启动时尝试初始化用户表；失败则降级为内存存储
     */
    @PostConstruct
    public void init() {
        if (jdbcTemplate == null) {
            log.info("未配置数据源，用户数据使用内存存储（重启后丢失）");
            return;
        }
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS ai_user (
                        id BIGSERIAL PRIMARY KEY,
                        user_account VARCHAR(64) NOT NULL UNIQUE,
                        user_password VARCHAR(128) NOT NULL,
                        user_name VARCHAR(64),
                        user_role VARCHAR(16) NOT NULL DEFAULT 'user',
                        create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            databaseAvailable = true;
            log.info("用户表 ai_user 初始化完成，使用数据库存储用户数据");
        } catch (Exception e) {
            log.warn("用户表初始化失败，降级为内存存储: {}", e.getMessage());
        }
    }

    /**
     * 发送邮箱验证码
     * <p>
     * 配置了邮件服务（spring.mail）时真实发送邮件并返回 null；
     * 未配置邮件服务（本地开发）时降级：不发送邮件，直接返回验证码，便于人工检验注册流程。
     *
     * @param email 接收验证码的邮箱
     * @return 验证码（仅未配置邮件服务时返回，真实发送时返回 null）
     */
    public String sendEmailCode(String email) {
        ThrowUtils.throwIf(StrUtil.isBlank(email) || !ReUtil.isMatch(EMAIL_REGEX, StrUtil.trim(email)),
                ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
        String trimmedEmail = StrUtil.trim(email);
        EmailCodeInfo existed = EMAIL_CODE_STORE.get(trimmedEmail);
        if (existed != null && System.currentTimeMillis() - existed.getSendTime() < RESEND_INTERVAL_MILLIS) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码发送过于频繁，请 60 秒后再试");
        }
        // 生成 6 位数字验证码
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        EMAIL_CODE_STORE.put(trimmedEmail, new EmailCodeInfo(code, System.currentTimeMillis()));
        // 尝试真实发送邮件
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender != null && StrUtil.isNotBlank(mailFromAddress)) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFromAddress);
                message.setTo(trimmedEmail);
                message.setSubject("恋吧AI超级智能体 - 注册验证码");
                message.setText("您的注册验证码是：" + code + "，5 分钟内有效，请勿泄露给他人。");
                mailSender.send(message);
                log.info("验证码邮件已发送至 {}", trimmedEmail);
                return null;
            } catch (Exception e) {
                log.error("验证码邮件发送失败，降级为接口返回: {}", e.getMessage());
                return code;
            }
        }
        log.info("未配置邮件服务，开发模式验证码（{}）：{}", trimmedEmail, code);
        return code;
    }

    /**
     * 用户注册（邮箱验证码校验，注册账号即邮箱）
     *
     * @param email          邮箱（同时作为登录账号）
     * @param emailCode      邮箱验证码
     * @param userPassword   密码
     * @param checkPassword  确认密码
     * @return 新用户 ID
     */
    public long userRegister(String email, String emailCode, String userPassword, String checkPassword) {
        // 参数校验
        ThrowUtils.throwIf(StrUtil.hasBlank(email, emailCode, userPassword, checkPassword), ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(!ReUtil.isMatch(EMAIL_REGEX, StrUtil.trim(email)), ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
        ThrowUtils.throwIf(userPassword.length() < 8, ErrorCode.PARAMS_ERROR, "密码长度不能小于 8 位");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword), ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        String trimmedEmail = StrUtil.trim(email);
        // 校验验证码
        EmailCodeInfo codeInfo = EMAIL_CODE_STORE.get(trimmedEmail);
        ThrowUtils.throwIf(codeInfo == null || !codeInfo.getCode().equals(emailCode),
                ErrorCode.PARAMS_ERROR, "验证码错误");
        ThrowUtils.throwIf(System.currentTimeMillis() - codeInfo.getSendTime() > CODE_EXPIRE_MILLIS,
                ErrorCode.PARAMS_ERROR, "验证码已过期，请重新获取");
        // 加密
        String encryptPassword = encryptPassword(userPassword);
        synchronized (trimmedEmail.intern()) {
            // 账号不能重复
            ThrowUtils.throwIf(findByAccount(trimmedEmail) != null, ErrorCode.PARAMS_ERROR, "该邮箱已注册");
            User user = new User();
            user.setUserAccount(trimmedEmail);
            user.setUserPassword(encryptPassword);
            user.setUserName("用户" + trimmedEmail.substring(0, trimmedEmail.indexOf('@')));
            user.setUserRole(UserConstant.DEFAULT_ROLE);
            user.setCreateTime(new Date());
            long userId = saveUser(user);
            // 注册成功后销毁验证码，防止重复使用
            EMAIL_CODE_STORE.remove(trimmedEmail);
            return userId;
        }
    }

    /**
     * 用户登录：校验通过后将登录态写入 Session
     */
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        ThrowUtils.throwIf(StrUtil.hasBlank(userAccount, userPassword), ErrorCode.PARAMS_ERROR, "参数为空");
        User user = findByAccount(userAccount);
        if (user == null || !user.getUserPassword().equals(encryptPassword(userPassword))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        LoginUserVO loginUserVO = toLoginUserVO(user);
        // 记录登录态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, loginUserVO);
        return loginUserVO;
    }

    /**
     * 获取当前登录用户（未登录抛出 NOT_LOGIN_ERROR）
     */
    public LoginUserVO getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (!(userObj instanceof LoginUserVO loginUserVO)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return loginUserVO;
    }

    /**
     * 获取当前登录用户（未登录返回 null，不抛异常）
     */
    public LoginUserVO getLoginUserIfPresent(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        return userObj instanceof LoginUserVO loginUserVO ? loginUserVO : null;
    }

    /**
     * 更新当前登录用户的昵称（user_name），并同步 Session 中的登录态快照，
     * 保证修改后 /user/current 立即返回新昵称，无需重新登录。
     *
     * @param userId   用户 ID（取自登录态，前端无需传）
     * @param userName 新昵称（非空且不超过 20 字）
     * @param request  HTTP 请求（用于更新 Session 登录态）
     * @return 更新后的登录用户信息
     */
    public LoginUserVO updateUserNickname(long userId, String userName, HttpServletRequest request) {
        String trimmedName = StrUtil.trim(userName);
        ThrowUtils.throwIf(StrUtil.isBlank(trimmedName), ErrorCode.PARAMS_ERROR, "昵称不能为空");
        ThrowUtils.throwIf(trimmedName.length() > 20, ErrorCode.PARAMS_ERROR, "昵称长度不能超过 20 字");
        User user = findById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        if (databaseAvailable) {
            try {
                jdbcTemplate.update("UPDATE ai_user SET user_name = ? WHERE id = ?", trimmedName, userId);
            } catch (Exception e) {
                log.error("更新用户昵称失败: {}", e.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "昵称保存失败，请稍后重试");
            }
        } else {
            user.setUserName(trimmedName);
        }
        // 同步 Session 登录态，确保右上角昵称立即生效
        LoginUserVO loginUserVO = toLoginUserVO(findById(userId));
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, loginUserVO);
        return loginUserVO;
    }

    /**
     * 用户注销：清除 Session 登录态
     */
    public boolean userLogout(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        ThrowUtils.throwIf(userObj == null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    private String encryptPassword(String userPassword) {
        return DigestUtil.md5Hex((SALT + userPassword).getBytes());
    }

    private LoginUserVO toLoginUserVO(User user) {
        LoginUserVO loginUserVO = new LoginUserVO();
        loginUserVO.setId(user.getId());
        loginUserVO.setUserAccount(user.getUserAccount());
        loginUserVO.setUserName(user.getUserName());
        loginUserVO.setUserRole(user.getUserRole());
        loginUserVO.setCreateTime(user.getCreateTime());
        return loginUserVO;
    }

    private User findById(long userId) {
        if (databaseAvailable) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT id, user_account, user_password, user_name, user_role, create_time FROM ai_user WHERE id = ?",
                        userId);
                if (rows.isEmpty()) {
                    return null;
                }
                return mapRowToUser(rows.get(0));
            } catch (Exception e) {
                log.error("查询用户失败: {}", e.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询用户失败");
            }
        }
        return memoryUserStore.values().stream()
                .filter(u -> u.getId() != null && u.getId() == userId)
                .findFirst()
                .orElse(null);
    }

    private User findByAccount(String userAccount) {
        if (databaseAvailable) {
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT id, user_account, user_password, user_name, user_role, create_time FROM ai_user WHERE user_account = ?",
                        userAccount);
                if (rows.isEmpty()) {
                    return null;
                }
                return mapRowToUser(rows.get(0));
            } catch (Exception e) {
                log.error("查询用户失败: {}", e.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "查询用户失败");
            }
        }
        return memoryUserStore.get(userAccount);
    }

    private long saveUser(User user) {
        if (databaseAvailable) {
            try {
                Long id = jdbcTemplate.queryForObject(
                        "INSERT INTO ai_user (user_account, user_password, user_name, user_role) VALUES (?, ?, ?, ?) RETURNING id",
                        Long.class,
                        user.getUserAccount(), user.getUserPassword(), user.getUserName(), user.getUserRole());
                user.setId(id);
                return id != null ? id : 0L;
            } catch (Exception e) {
                log.error("保存用户失败: {}", e.getMessage());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，请稍后重试");
            }
        }
        long id = memoryIdGenerator.getAndIncrement();
        user.setId(id);
        memoryUserStore.put(user.getUserAccount(), user);
        return id;
    }

    private User mapRowToUser(Map<String, Object> row) {
        User user = new User();
        user.setId(((Number) row.get("id")).longValue());
        user.setUserAccount((String) row.get("user_account"));
        user.setUserPassword((String) row.get("user_password"));
        user.setUserName((String) row.get("user_name"));
        user.setUserRole((String) row.get("user_role"));
        Object createTime = row.get("create_time");
        if (createTime instanceof java.sql.Timestamp timestamp) {
            user.setCreateTime(new Date(timestamp.getTime()));
        }
        return user;
    }

    /**
     * 邮箱验证码信息（验证码 + 发送时间）
     */
    @Data
    private static class EmailCodeInfo {
        private final String code;
        private final long sendTime;
    }
}
