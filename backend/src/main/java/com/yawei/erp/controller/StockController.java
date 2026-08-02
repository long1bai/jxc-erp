package com.yawei.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.yawei.erp.common.ApiResponse;
import com.yawei.erp.common.PageResult;
import com.yawei.erp.mapper.StockMapper;

/** 库存查询 + 库存流水 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockMapper mapper;

    public StockController(StockMapper mapper) {
        this.mapper = mapper;
    }

    /** 当前库存（搜索/分页/按仓库） */
    @GetMapping("/inventory")
    public Map<String, Object> inventory(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.inventory(keyword.trim(), warehouseId, p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    /** 库存流水（按物料或全部） */
    @GetMapping("/movements")
    public Map<String, Object> movements(
            @RequestParam(defaultValue = "0") long materialId,
            @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(Map.of("items",
                mapper.movements(materialId > 0 ? materialId : null, Math.min(Math.max(limit, 1), 500))));
    }
}
