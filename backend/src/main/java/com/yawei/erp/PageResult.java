package com.yawei.erp;

import java.util.List;
import java.util.Map;

/**
 * 统一分页响应（企业规范）：items + total + page + size
 * 前端兼容：仅依赖 items/total 字段，page/size 为补充元数据
 */
public record PageResult(List<?> items, long total, long page, long size) {

    public Map<String, Object> toMap() {
        return Map.of("items", items, "total", total, "page", page, "size", size);
    }
}
