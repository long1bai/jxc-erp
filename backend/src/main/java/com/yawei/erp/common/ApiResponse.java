package com.yawei.erp.common;

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
}
