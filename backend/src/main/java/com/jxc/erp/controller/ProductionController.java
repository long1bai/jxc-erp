package com.jxc.erp.controller;

import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.common.PageResult;
import com.jxc.erp.dto.ProductionDtos.PiReq;
import com.jxc.erp.dto.ProductionDtos.PrtReq;
import com.jxc.erp.service.ProductionService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 生产管理：成品入库、生产退料、统计。
 * 业务逻辑已抽取至 ProductionService（企业化分层 2026-08-02）。
 */
@RestController
public class ProductionController {

    private final ProductionService service;

    public ProductionController(ProductionService service) {
        this.service = service;
    }

    // ============ 成品入库 ============

    @GetMapping("/api/production-ins")
    public Map<String, Object> piList(@RequestParam(defaultValue = "") String keyword,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var data = service.piList(keyword, page, size);
        return ApiResponse.ok(new PageResult((List<?>) data.get("items"), (long) data.get("total"),
                (long) data.get("page"), (long) data.get("size")).toMap());
    }

    @GetMapping("/api/production-ins/{id}")
    public Map<String, Object> piDetail(@PathVariable Long id) {
        var result = service.piDetail(id);
        if (result == null) {
            return ApiResponse.fail("入库单不存在");
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/api/production-ins")
    @Transactional
    public Map<String, Object> piCreate(@RequestBody PiReq req) {
        if (req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请添加产品明细");
        }
        var result = service.piCreate(req);
        if (result.containsKey("error")) {
            return ApiResponse.fail((String) result.get("error"));
        }
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/api/production-ins/{id}")
    @Transactional
    public Map<String, Object> piDelete(@PathVariable Long id) {
        if (!service.piDelete(id)) {
            return ApiResponse.fail("入库单不存在");
        }
        return ApiResponse.ok();
    }

    // ============ 生产退料 ============

    @GetMapping("/api/production-returns")
    public Map<String, Object> prtList(@RequestParam(defaultValue = "") String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        var data = service.prtList(keyword, page, size);
        return ApiResponse.ok(new PageResult((List<?>) data.get("items"), (long) data.get("total"),
                (long) data.get("page"), (long) data.get("size")).toMap());
    }

    @GetMapping("/api/production-returns/{id}")
    public Map<String, Object> prtDetail(@PathVariable Long id) {
        var result = service.prtDetail(id);
        if (result == null) {
            return ApiResponse.fail("退料单不存在");
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/api/production-returns")
    @Transactional
    public Map<String, Object> prtCreate(@RequestBody PrtReq req) {
        if (req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请添加退料明细");
        }
        return ApiResponse.ok(service.prtCreate(req));
    }

    @DeleteMapping("/api/production-returns/{id}")
    @Transactional
    public Map<String, Object> prtDelete(@PathVariable Long id) {
        if (!service.prtDelete(id)) {
            return ApiResponse.fail("退料单不存在");
        }
        return ApiResponse.ok();
    }

    // ============ 统计 ============

    @GetMapping("/api/production-stats/ins")
    public Map<String, Object> inStats(@RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", service.inStats(from, to)));
    }

    @GetMapping("/api/production-stats/returns")
    public Map<String, Object> returnStats(@RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", service.returnStats(from, to)));
    }
}
