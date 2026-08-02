package com.yawei.erp.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.yawei.erp.security.SessionStore;
import com.yawei.erp.entity.OperationLog;
import com.yawei.erp.mapper.OperationLogMapper;
import com.yawei.erp.security.TokenUtils;
import com.yawei.erp.entity.OperationLog;
import com.yawei.erp.mapper.OperationLogMapper;


/** 操作日志拦截器：自动记录所有写操作（POST/PUT/DELETE）——谁在什么时间做了什么 */
@Component
public class OperationLogInterceptor implements HandlerInterceptor {

    private static final ThreadLocal<Long> START = new ThreadLocal<>();

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private OperationLogMapper logMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        START.set(System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        String method = request.getMethod();
        // 只记录写操作（GET 查询量大且无审计价值）
        if (!("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) {
            return;
        }
        long start = START.get() == null ? System.currentTimeMillis() : START.get();
        START.remove();
        try {
            String auth = request.getHeader("Authorization");
            SessionStore.SessionUser u = sessionStore.verify(TokenUtils.extractToken(auth));
            String path = request.getRequestURI();
            String ip = request.getRemoteAddr();
            if (ip == null) ip = "";
            logMapper.insert(new OperationLog(
                    u == null ? "" : u.username(),
                    u == null ? "" : u.role(),
                    method,
                    path,
                    guessModule(path),
                    guessDetail(path, method),
                    ip,
                    response.getStatus(),
                    (int) (System.currentTimeMillis() - start)));
        } catch (Exception ignored) {
            // 日志失败不影响业务
        }
    }

    /** 按接口路径推断模块 */
    static String guessModule(String path) {
        if (path.contains("/auth/")) return "系统";
        if (path.contains("/work/")) return "报工";
        if (path.contains("/orders") || path.contains("/deliveries")) return "销售";
        if (path.contains("/purchases") || path.contains("/returns") || path.contains("/photo")) return "采购";
        if (path.contains("/stock") || path.contains("/takes")) return "库存";
        if (path.contains("/vouchers") || path.contains("/income") || path.contains("/transfers")
                || path.contains("/settle") || path.contains("/aging")) return "财务";
        if (path.contains("/materials") || path.contains("/customers") || path.contains("/suppliers")
                || path.contains("/warehouses") || path.contains("/bom") || path.contains("/express")) return "基础资料";
        if (path.contains("/production")) return "生产";
        if (path.contains("/users") || path.contains("/backup") || path.contains("/menus")) return "系统";
        return "其他";
    }

    /** 生成可读的操作描述 */
    static String guessDetail(String path, String method) {
        if (path.endsWith("/auth/login")) return "登录系统";
        if (path.endsWith("/auth/logout")) return "退出登录";
        String action = "POST".equals(method) ? "新增/提交" : "PUT".equals(method) ? "修改" : "删除";
        // 路径最后一段（不含 id）作为对象名
        String[] segs = path.split("/");
        String last = segs.length > 0 ? segs[segs.length - 1] : path;
        if (last.matches("\\d+")) {
            last = segs.length > 1 ? segs[segs.length - 2] : last;
        }
        return action + "：" + last;
    }
}
