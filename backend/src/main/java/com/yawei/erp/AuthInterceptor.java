package com.yawei.erp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/** 全局鉴权拦截器（2026-08-01 新增）
 *  背景：安全扫描确认 172 个 /api 端点中 171 个无 token 可访问（鉴权散落在各 Controller 且漏覆盖），
 *  含读取全部业务数据、触发备份恢复（可回滚数据库）——配合花生壳外网映射风险为灾难级。
 *  本拦截器统一鉴权：覆盖 /api/**，仅排除 /api/auth/login。
 *  前端 request.js 已处理 401（清会话跳登录），SessionStore 校验失败返回 401 JSON。
 *  注意：OperationLogInterceptor 仅记日志不鉴权（preHandle 直接 return true），二者职责分离。 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private SessionStore sessionStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        // CORS 预检请求直接放行（WebConfig 已配置 allowedMethods 含 OPTIONS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (sessionStore.verify(AuthController.extractToken(auth)) != null) {
            return true;
        }
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"error\":\"未登录或登录已过期\"}");
        return false;
    }
}
