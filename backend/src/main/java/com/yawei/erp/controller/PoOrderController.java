package com.yawei.erp.controller;

import com.yawei.erp.common.ApiResponse;
import com.yawei.erp.common.PageResult;
import com.yawei.erp.dto.PoOrderDtos.CreateReq;
import com.yawei.erp.dto.PoOrderDtos.ReceiveReq;
import com.yawei.erp.service.PoOrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 独立采购订单（订单 → 分批入库 → 执行跟踪）。
 * 业务逻辑已抽取至 PoOrderService（企业化分层 2026-08-02）。
 */
@RestController
@RequestMapping("/api/po-orders")
public class PoOrderController {

    private final PoOrderService service;

    public PoOrderController(PoOrderService service) {
        this.service = service;
    }

    /** 创建采购订单 */
    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody CreateReq req) {
        if (req.supplierId() == null || req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请选择供应商并添加订单明细");
        }
        return ApiResponse.ok(service.create(req));
    }

    /** 订单列表（keyword 搜单号/供应商，status 过滤） */
    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "") String keyword,
                                    @RequestParam(defaultValue = "") String status,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        var data = service.list(keyword, status, page, size);
        return ApiResponse.ok(new PageResult((List<?>) data.get("items"), (long) data.get("total"),
                (long) data.get("page"), (long) data.get("size")).toMap());
    }

    /** 订单详情（含明细 + 已收数量） */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        var result = service.detail(id);
        if (result == null) {
            return ApiResponse.fail("采购订单不存在");
        }
        return ApiResponse.ok(result);
    }

    /** 按订单入库（分批）：生成入库单 + 加库存 + 更新订单执行状态 */
    @PostMapping("/{id}/receive")
    @Transactional
    public Map<String, Object> receive(@PathVariable Long id, @RequestBody List<ReceiveReq> items) {
        if (items == null || items.isEmpty()) {
            return ApiResponse.fail("请填写本次入库数量");
        }
        var result = service.receive(id, items);
        if (result.containsKey("error")) {
            return ApiResponse.fail((String) result.get("error"));
        }
        return ApiResponse.ok(result);
    }

    /** 删除订单（仅未入库或部分入库未完成时允许；有入库关联的禁止） */
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        var result = service.delete(id);
        if (result.containsKey("error")) {
            return ApiResponse.fail((String) result.get("error"));
        }
        return ApiResponse.ok();
    }
}
