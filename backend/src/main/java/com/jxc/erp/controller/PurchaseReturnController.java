package com.jxc.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.common.PageResult;
import com.jxc.erp.util.SequenceUtil;
import com.jxc.erp.mapper.ReturnMapper;
import com.jxc.erp.mapper.SysMapper;
import com.jxc.erp.mapper.TradeMapper;
import com.jxc.erp.service.SysConfigService;

/** 采购退货：退回供应商，自动扣减库存；删除=回补库存 */
@RestController
@RequestMapping("/api/purchase-returns")
public class PurchaseReturnController {

    private final ReturnMapper mapper;
    private final SysMapper sys;
    private final TradeMapper trade;
    private final SequenceUtil seq;

        private final SysConfigService cfg;

public PurchaseReturnController(ReturnMapper mapper, SysMapper sys, TradeMapper trade, SequenceUtil seq, SysConfigService cfg) {
        this.mapper = mapper;
        this.cfg = cfg;
        this.sys = sys;
        this.trade = trade;
        this.seq = seq;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.returnList(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        var rows = mapper.returnExists(id);
        if (rows.isEmpty()) {
            return ApiResponse.fail("退货单不存在");
        }
        return ApiResponse.ok(Map.of("main", rows.get(0), "items", mapper.returnItems(id)));
    }

    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody ReturnReq req) {
        if (req.supplierId() == null || req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请选择供应商并填写退货明细");
        }
        String sname = sys.supplierName(req.supplierId());
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (var it : req.items()) {
            totalQty = totalQty.add(it.quantity());
            totalAmt = totalAmt.add(it.quantity().multiply(it.unitPrice() == null ? BigDecimal.ZERO : it.unitPrice()));
        }
        String date = req.returnDate() == null || req.returnDate().isBlank() ? today() : req.returnDate();
        String prNo = seq.nextDaily(cfg.get("seq_th"));
        mapper.returnInsert(prNo, req.supplierId(), sname == null ? "" : sname, date,
                req.poId(), totalQty, totalAmt.setScale(2, RoundingMode.HALF_UP), req.remark());
        Long prId = mapper.lastReturnId(prNo);
        int idx = 0;
        for (var it : req.items()) {
            BigDecimal amt = it.quantity().multiply(it.unitPrice() == null ? BigDecimal.ZERO : it.unitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            mapper.returnItemInsert(prId, it.materialId(), it.materialName() == null ? "" : it.materialName(),
                    it.spec() == null ? "" : it.spec(), it.unit() == null ? "" : it.unit(),
                    it.quantity(), it.unitPrice() == null ? BigDecimal.ZERO : it.unitPrice(), amt, idx++);
            if (it.materialId() != null) {
                BigDecimal before = stock(it.materialId());
                BigDecimal after = before.subtract(it.quantity());
                trade.movementInsert(it.materialId(), "out", "purchase_return", prId,
                        it.quantity(), before, after,
                        it.unitPrice() == null ? BigDecimal.ZERO : it.unitPrice(), amt, date, "退货#" + prNo + "出库");
            }
        }
        return ApiResponse.ok(Map.of("id", prId, "prNo", prNo));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.returnExists(id).isEmpty()) {
            return ApiResponse.fail("退货单不存在");
        }
        // 外键约束：先删明细 → 流水 → 主单
        mapper.returnItemsDelete(id);
        trade.movementsDeleteByRef("purchase_return", id);
        mapper.returnDelete(id);
        return ApiResponse.ok();
    }

    private BigDecimal stock(Long materialId) {
        BigDecimal v = sys.currentStock(materialId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private String today() {
        return LocalDate.now().toString();
    }

    public record ReturnReq(Long supplierId, String returnDate, Long poId, String remark,
                            List<ReturnItemReq> items) {}
    public record ReturnItemReq(Long materialId, String materialName, String spec, String unit,
                                BigDecimal quantity, BigDecimal unitPrice) {}
}
