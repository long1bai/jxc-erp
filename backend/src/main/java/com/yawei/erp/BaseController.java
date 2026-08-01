package com.yawei.erp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 新功能 Controller 基类（模板方法模式：分页/响应统一，新模块继承即得标准能力）
 *
 * 用法：public class XxxController extends BaseController { ... }
 *  - pageParams(page, size)：分页参数统一校验（page>=1, size 1~100）
 *  - ok()/ok(data)/fail(msg)：统一响应结构 ApiResponse
 *
 * 设计说明：所有新增业务模块的 Controller 一律继承本类，保证
 * 分页边界、响应格式、异常语义全项目一致；现有 Controller 逐步迁移。
 */
public abstract class BaseController {

    /** 分页参数统一校验（模板方法：新模块不用重复写边界判断） */
    protected <T> Page<T> pageParams(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
    }

    /** 统一响应 */
    protected Map<String, Object> ok() {
        return ApiResponse.ok();
    }

    protected Map<String, Object> ok(Object data) {
        return ApiResponse.ok(data);
    }

    protected Map<String, Object> fail(String message) {
        return ApiResponse.fail(message);
    }

    /** 分页结果（items + total + current + size 统一结构） */
    protected Map<String, Object> pageResult(java.util.List<?> items, long total, long current, long size) {
        return ApiResponse.ok(new PageResult(items, total, current, size).toMap());
    }
}
