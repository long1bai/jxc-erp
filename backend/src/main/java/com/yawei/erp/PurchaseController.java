package com.yawei.erp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** 采购入库单（供应商送货→入库，自动加库存 + 写流水） */
@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final TradeMapper mapper;
    private final SysMapper sys;
    private final SequenceUtil seq;

    public PurchaseController(TradeMapper mapper, SysMapper sys, SequenceUtil seq) {
        this.mapper = mapper;
        this.sys = sys;
        this.seq = seq;
    }

    public record ItemReq(Long materialId, String materialName, String spec, String unit,
                          BigDecimal quantity, BigDecimal unitPrice) {}

    public record CreateReq(Long supplierId, String poDate, String remark, Long warehouseId,
                            List<ItemReq> items) {}

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
    @Transactional
    public Map<String, Object> create(@RequestBody CreateReq req) {
        if (req.supplierId() == null || req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请选择供应商并添加明细");
        }
        String poNo = seq.nextDaily("CGDD");
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (var it : req.items()) {
            totalQty = totalQty.add(it.quantity() == null ? BigDecimal.ZERO : it.quantity());
            totalAmt = totalAmt.add(amount(it.quantity(), it.unitPrice()));
        }
        mapper.purchaseInsert(poNo, req.supplierId(),
                req.poDate() == null || req.poDate().isBlank() ? today() : req.poDate(),
                totalQty, totalAmt, req.remark());
        Long poId = mapper.lastPurchaseId(poNo);
        int sort = 0;
        for (var it : req.items()) {
            if (it.materialId() == null || it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            BigDecimal amt = amount(it.quantity(), it.unitPrice());
            mapper.purchaseItemInsert(poId, it.materialId(), it.materialName(), it.spec(), it.unit(),
                    it.quantity(), it.unitPrice(), amt, sort++);
            BigDecimal before = currentStock(it.materialId());
            BigDecimal after = before.add(it.quantity());
            mapper.movementInsertWh(it.materialId(), req.warehouseId(), "in", "purchase", poId,
                    it.quantity(), before, after, it.unitPrice(), amt, today(), "采购入库#" + poNo);
        }
        return ApiResponse.ok(Map.of("id", poId, "poNo", poNo));
    }

    @DeleteMapping("/{id}")
    @Transactional
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

    private BigDecimal currentStock(Long materialId) {
        BigDecimal v = sys.currentStock(materialId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal amount(BigDecimal qty, BigDecimal price) {
        if (qty == null || price == null) {
            return BigDecimal.ZERO;
        }
        return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    private String today() {
        return java.time.LocalDate.now().toString();
    }
}
