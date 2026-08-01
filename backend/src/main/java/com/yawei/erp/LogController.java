package com.yawei.erp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 操作日志查询 + 前端错误上报 */
@RestController
@RequestMapping("/api/logs")
public class LogController {

    @Autowired
    private OperationLogMapper logMapper;

    @Autowired
    private SessionStore sessionStore;

    /** 查询操作日志（关键词/日期区间，最近 N 条） */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String kw,
            @RequestParam(defaultValue = "") String start,
            @RequestParam(defaultValue = "") String end,
            @RequestParam(defaultValue = "500") int size) {
        List<Map<String, Object>> items = logMapper.query(kw.trim(), start, end,
                Math.min(Math.max(size, 1), 1000));
        return ApiResponse.ok(Map.of("items", items));
    }

    /** 前端错误上报（window.onerror / unhandledrejection） */
    @PostMapping("/client-error")
    public Map<String, Object> clientError(@RequestBody(required = false) Map<String, Object> body,
                                           @RequestHeader(value = "Authorization", required = false) String auth) {
        SessionStore.SessionUser u = sessionStore.verify(AuthController.extractToken(auth));
        String msg = body == null ? "" : String.valueOf(body.getOrDefault("message", ""));
        String url = body == null ? "" : String.valueOf(body.getOrDefault("url", ""));
        if (msg.length() > 200) msg = msg.substring(0, 200);
        try {
            logMapper.insert(new OperationLog(
                    u == null ? "" : u.username(),
                    u == null ? "" : u.role(),
                    "ERR",
                    url,
                    "前端",
                    "前端错误：" + msg,
                    "",
                    0, 0));
        } catch (Exception ignored) {
        }
        return ApiResponse.ok();
    }
}
