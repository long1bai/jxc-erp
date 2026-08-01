package com.yawei.erp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 生产管理：成品入库（生产完成的产品入仓，in 流水；报工已自动扣材料，入库不重复扣料）
 *  生产退料（车间剩余/多扣材料退回仓库，in 流水冲回） */
@RestController
public class ProductionController {

    private final ProductionMapper mapper;
    private final SysMapper sys;
    private final TradeMapper trade;
    private final MaterialMapper materials;
    private final SequenceUtil seq;

    public ProductionController(ProductionMapper mapper, SysMapper sys, TradeMapper trade,
                                MaterialMapper materials, SequenceUtil seq) {
        this.mapper = mapper;
        this.sys = sys;
        this.trade = trade;
        this.materials = materials;
        this.seq = seq;
    }

    // ============ 成品入库 ============

    @GetMapping("/api/production-ins")
    public Map<String, Object> piList(@RequestParam(defaultValue = "") String keyword,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.piList(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @GetMapping("/api/production-ins/{id}")
    public Map<String, Object> piDetail(@PathVariable Long id) {
        var rows = mapper.piExists(id);
        if (rows.isEmpty()) {
            return ApiResponse.fail("入库单不存在");
        }
        return ApiResponse.ok(Map.of("main", rows.get(0), "items", mapper.piItems(id)));
    }

    @PostMapping("/api/production-ins")
    @Transactional
    public Map<String, Object> piCreate(@RequestBody PiReq req) {
        if (req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请添加产品明细");
        }
        String date = req.inDate() == null || req.inDate().isBlank() ? today() : req.inDate();
        String piNo = seq.nextDaily(Constants.SEQ_PRODUCT_IN);
        BigDecimal totalQty = BigDecimal.ZERO;
        int totalItems = 0;
        mapper.piInsert(piNo, date, BigDecimal.ZERO, 0, req.remark() == null ? "" : req.remark());
        Long piId = mapper.lastPiId(piNo);
        int idx = 0;
        for (var it : req.items()) {
            if (it.productId() == null) continue;
            Material m = materials.selectById(it.productId());
            if (m == null) continue;
            BigDecimal qty = it.quantity() == null ? BigDecimal.ZERO : it.quantity();
            BigDecimal cost = m.getPurchasePrice() == null ? BigDecimal.ZERO : m.getPurchasePrice();
            BigDecimal amount = qty.multiply(cost).setScale(2, RoundingMode.HALF_UP);
            mapper.piItemInsert(piId, it.productId(), m.getName() == null ? "" : m.getName(),
                    m.getSpec() == null ? "" : m.getSpec(), m.getUnit() == null ? "" : m.getUnit(),
                    qty, cost, amount, idx++);
            BigDecimal before = stock(it.productId());
            BigDecimal after = before.add(qty);
            trade.movementInsert(it.productId(), "in", "production_in", piId,
                    qty, before, after, cost, amount, date, "成品入库#" + piNo);
            totalQty = totalQty.add(qty);
            totalItems++;
        }
        if (totalItems == 0) {
            return ApiResponse.fail("没有有效的产品明细");
        }
        mapper.piSummaryUpdate(piId, totalQty, totalItems);
        return ApiResponse.ok(Map.of("id", piId, "piNo", piNo));
    }

    @DeleteMapping("/api/production-ins/{id}")
    @Transactional
    public Map<String, Object> piDelete(@PathVariable Long id) {
        if (mapper.piExists(id).isEmpty()) {
            return ApiResponse.fail("入库单不存在");
        }
        mapper.piItemsDelete(id);
        trade.movementsDeleteByRef("production_in", id);
        mapper.piDelete(id);
        return ApiResponse.ok();
    }

    // ============ 生产退料 ============

    @GetMapping("/api/production-returns")
    public Map<String, Object> prtList(@RequestParam(defaultValue = "") String keyword,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.prtList(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @GetMapping("/api/production-returns/{id}")
    public Map<String, Object> prtDetail(@PathVariable Long id) {
        var rows = mapper.prtExists(id);
        if (rows.isEmpty()) {
            return ApiResponse.fail("退料单不存在");
        }
        return ApiResponse.ok(Map.of("main", rows.get(0), "items", mapper.prtItems(id)));
    }

    @PostMapping("/api/production-returns")
    @Transactional
    public Map<String, Object> prtCreate(@RequestBody PrtReq req) {
        if (req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请添加退料明细");
        }
        String date = req.returnDate() == null || req.returnDate().isBlank() ? today() : req.returnDate();
        String prtNo = seq.nextDaily(Constants.SEQ_PRODUCT_RETURN);
        BigDecimal totalQty = BigDecimal.ZERO;
        mapper.prtInsert(prtNo, date, BigDecimal.ZERO, req.remark() == null ? "" : req.remark());
        Long prtId = mapper.lastPrtId(prtNo);
        int idx = 0;
        for (var it : req.items()) {
            if (it.materialId() == null) continue;
            Material m = materials.selectById(it.materialId());
            if (m == null) continue;
            BigDecimal qty = it.quantity() == null ? BigDecimal.ZERO : it.quantity();
            mapper.prtItemInsert(prtId, it.materialId(), m.getName() == null ? "" : m.getName(),
                    m.getSpec() == null ? "" : m.getSpec(), m.getUnit() == null ? "" : m.getUnit(),
                    qty, idx++);
            BigDecimal before = stock(it.materialId());
            BigDecimal after = before.add(qty);
            BigDecimal cost = m.getPurchasePrice() == null ? BigDecimal.ZERO : m.getPurchasePrice();
            trade.movementInsert(it.materialId(), "in", "production_return", prtId,
                    qty, before, after, cost, qty.multiply(cost).setScale(2, RoundingMode.HALF_UP),
                    date, "生产退料#" + prtNo);
            totalQty = totalQty.add(qty);
        }
        mapper.prtSummaryUpdate(prtId, totalQty);
        return ApiResponse.ok(Map.of("id", prtId, "prtNo", prtNo));
    }

    @DeleteMapping("/api/production-returns/{id}")
    @Transactional
    public Map<String, Object> prtDelete(@PathVariable Long id) {
        if (mapper.prtExists(id).isEmpty()) {
            return ApiResponse.fail("退料单不存在");
        }
        mapper.prtItemsDelete(id);
        trade.movementsDeleteByRef("production_return", id);
        mapper.prtDelete(id);
        return ApiResponse.ok();
    }

    // ============ 统计 ============

    @GetMapping("/api/production-stats/ins")
    public Map<String, Object> inStats(@RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", mapper.productionInStats(from, to)));
    }

    @GetMapping("/api/production-stats/returns")
    public Map<String, Object> returnStats(@RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        return ApiResponse.ok(Map.of("items", mapper.productionReturnStats(from, to)));
    }

    private BigDecimal stock(Long materialId) {
        BigDecimal v = sys.currentStock(materialId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private String today() {
        return LocalDate.now().toString();
    }

    public record PiReq(String inDate, String remark, List<PiItemReq> items) {}
    public record PiItemReq(Long productId, BigDecimal quantity) {}
    public record PrtReq(String returnDate, String remark, List<PrtItemReq> items) {}
    public record PrtItemReq(Long materialId, BigDecimal quantity) {}
}
