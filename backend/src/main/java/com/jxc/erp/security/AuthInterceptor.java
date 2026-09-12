package com.jxc.erp.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import com.jxc.erp.common.Constants;
import com.jxc.erp.config.WebConfig;
import com.jxc.erp.interceptor.OperationLogInterceptor;
import com.jxc.erp.security.TokenUtils;


/** 全局鉴权拦截器（2026-08-01 新增）
 *  背景：安全扫描确认 172 个 /api 端点中 171 个无 token 可访问（鉴权散落在各 Controller 且漏覆盖），
 *  含读取全部业务数据、触发备份恢复（可回滚数据库）——配合花生壳外网映射风险为灾难级。
 *  本拦截器统一鉴权：覆盖 /api/**，仅排除 /api/auth/login。
 *  2026-08-01 二次增强：越权测试确认普通用户(role=user/employee)可创建 admin 账号、触发备份、删除单据——
 *  垂直越权。因此叠加管理专属校验：用户管理/备份/配置/字典/日志路径 + 所有 DELETE 方法仅限管理角色。
 *  前端 request.js 已处理 401（清会话跳登录）；403 返回"仅管理员"提示。
 *  注意：OperationLogInterceptor 仅记日志不鉴权（preHandle 直接 return true），二者职责分离。 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 管理角色白名单（Constants: admin/dev/boss），employee/user 视为普通用户 */
    private static final java.util.Set<String> ADMIN_ROLES = java.util.Set.of("admin", "dev", "boss");

    @Autowired
    private SessionStore sessionStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        // CORS 预检请求直接放行（WebConfig 已配置 allowedMethods 含 OPTIONS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        SessionStore.SessionUser u = sessionStore.verify(TokenUtils.extractToken(auth));
        if (u == null) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"未登录或登录已过期\"}");
            return false;
        }
        if (isAdminOnly(request)) {
            if (!ADMIN_ROLES.contains(u.role())) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"error\":\"无权限：该操作仅限管理员\"}");
                return false;
            }
        }
        return true;
    }

    /** 管理专属判定：用户管理/备份/系统配置/字典/日志路径 + 所有删除方法 */
    private boolean isAdminOnly(HttpServletRequest request) {
        if ("DELETE".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/api/users")
                || path.startsWith("/api/backup")
                || path.startsWith("/api/config")
                || path.startsWith("/api/dicts")
                || path.startsWith("/api/logs");
    }
}
