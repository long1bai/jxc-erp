package com.jxc.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.common.PageResult;
import com.jxc.erp.mapper.TradeMapper;
import com.jxc.erp.service.PurchaseCreationService;

/** 采购入库单（供应商送货→入库，自动加库存 + 写流水） */
@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final TradeMapper mapper;
    private final PurchaseCreationService creationService;

    public PurchaseController(TradeMapper mapper, PurchaseCreationService creationService) {
        this.mapper = mapper;
        this.creationService = creationService;
    }

    public record ItemReq(Long materialId, String materialName, String spec, String unit,
                          String assemblySystem, BigDecimal quantity, BigDecimal unitPrice) {}

    public record CreateReq(Long supplierId, String poDate, String remark, Long warehouseId,
                            Long projectId, String projectName, String handler, List<ItemReq> items,
                            Map<String, Object> ext) {}

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.purchaseList(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        var main = mapper.purchaseDetail(id);
        if (main.isEmpty()) {
            return ApiResponse.fail("采购单不存在");
        }
        return ApiResponse.ok(Map.of("main", main.get(0), "items", mapper.purchaseItems(id)));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateReq req) {
        if (req.supplierId() == null || req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请选择供应商并添加明细");
        }
        List<PurchaseCreationService.Line> lines = new java.util.ArrayList<>();
        for (ItemReq it : req.items()) {
            lines.add(new PurchaseCreationService.Line(it.materialId(), it.materialName(), it.spec(), it.unit(),
                    it.assemblySystem(), it.quantity(), it.unitPrice()));
        }
        var draft = new PurchaseCreationService.Draft(null, req.supplierId(), req.projectId(), req.projectName(),
                req.poDate(), req.handler(), req.remark(), req.warehouseId(), lines);
        var result = creationService.create(draft, true);
        // 动态字段 ext_json 落库（单据能力中心）
        if (req.ext() != null && !req.ext().isEmpty()) {
            try {
                mapper.purchaseUpdateExt((Long) result.get("id"), new ObjectMapper().writeValueAsString(req.ext()));
            } catch (Exception e) {
                return ApiResponse.fail("动态字段保存失败：" + e.getMessage());
            }
        }
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.purchaseDetail(id).isEmpty()) {
            return ApiResponse.fail("采购单不存在");
        }
        // 外键约束：先删明细 → 流水 → 主单
        mapper.purchaseItemsDelete(id);
        mapper.movementsDeleteByRef("purchase", id);
        mapper.purchaseDelete(id);
        return ApiResponse.ok();
    }
}
