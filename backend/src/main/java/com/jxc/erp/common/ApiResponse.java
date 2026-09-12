package com.jxc.erp.common;

import java.util.Map;

/** 统一 API 返回结构 */
public class ApiResponse {

    public static Map<String, Object> ok(Object data) {
        return Map.of("success", true, "data", data == null ? Map.of() : data);
    }

    public static Map<String, Object> ok() {
        return Map.of("success", true);
    }

    public static Map<String, Object> fail(String error) {
        return Map.of("success", false, "error", error == null ? "操作失败" : error);
    }

    /** 带错误明细的失败响应（如导入逐行错误，data 透传到前端 e.detail） */
    public static Map<String, Object> fail(String error, Object detail) {
        var m = new java.util.HashMap<String, Object>();
        m.put("success", false);
        m.put("error", error == null ? "操作失败" : error);
        m.put("data", detail == null ? Map.of() : detail);
        return m;
    }
}
