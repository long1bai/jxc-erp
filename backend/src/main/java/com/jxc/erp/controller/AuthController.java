package com.jxc.erp.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.security.PasswordUtils;
import com.jxc.erp.security.SessionStore;
import com.jxc.erp.entity.User;
import com.jxc.erp.mapper.UserMapper;
import com.jxc.erp.mapper.WorkMapper;
import com.jxc.erp.security.TokenUtils;

/**
 * 登录/会话 API（前端 Vite 应用调用）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final SessionStore sessionStore;
    private final WorkMapper workMapper;

    public AuthController(UserMapper userMapper, SessionStore sessionStore, WorkMapper workMapper) {
        this.userMapper = userMapper;
        this.sessionStore = sessionStore;
        this.workMapper = workMapper;
    }

    /** 关联的报工员工名（登录/me 返回，打卡页自动选中用） */
    private String workEmployeeName(Long workEmployeeId) {
        if (workEmployeeId == null) {
            return "";
        }
        var rows = workMapper.employeeExists(workEmployeeId);
        if (rows.isEmpty()) {
            return "";
        }
        Object n = rows.get(0).get("name");
        return n == null ? "" : String.valueOf(n);
    }

    public record LoginReq(String username, String password) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginReq req) {
        String username = req.username() == null ? "" : req.username().trim();
        String password = req.password() == null ? "" : req.password();
        if (username.isEmpty() || password.isEmpty()) {
            return ApiResponse.fail("用户名和密码不能为空");
        }
        var user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username));
        if (user == null) {
            return ApiResponse.fail("用户名不存在");
        }
        if (Boolean.FALSE.equals(user.getActive())) {
            return ApiResponse.fail("账号已停用");
        }
        if (!PasswordUtils.matches(req.password(), user.getPasswordHash())) {
            return ApiResponse.fail("密码错误");
        }
        String token = sessionStore.create(new SessionStore.SessionUser(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()));
        Map<String, Object> userMap = new java.util.HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("displayName", user.getDisplayName());
        userMap.put("role", user.getRole());
        userMap.put("workEmployeeId", user.getWorkEmployeeId());
        userMap.put("workEmployeeName", workEmployeeName(user.getWorkEmployeeId()));
        return ApiResponse.ok(Map.of("token", token, "user", userMap));
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String auth) {
        var user = currentUser(auth);
        if (user == null) {
            return ApiResponse.fail("未登录");
        }
        var full = userMapper.selectById(user.id());
        Map<String, Object> userMap = new java.util.HashMap<>();
        userMap.put("id", user.id());
        userMap.put("username", user.username());
        userMap.put("displayName", user.displayName());
        userMap.put("role", user.role());
        userMap.put("workEmployeeId", full == null ? null : full.getWorkEmployeeId());
        userMap.put("workEmployeeName", full == null ? "" : workEmployeeName(full.getWorkEmployeeId()));
        return ApiResponse.ok(userMap);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        sessionStore.destroy(TokenUtils.extractToken(auth));
        return ApiResponse.ok();
    }


    public SessionStore.SessionUser currentUser(String auth) {
        return sessionStore.verify(TokenUtils.extractToken(auth));
    }
}
