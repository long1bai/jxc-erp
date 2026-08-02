package com.yawei.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.yawei.erp.common.ApiResponse;
import com.yawei.erp.common.Constants;
import com.yawei.erp.common.PageResult;
import com.yawei.erp.util.SequenceUtil;
import com.yawei.erp.entity.Material;
import com.yawei.erp.mapper.MaterialMapper;
import com.yawei.erp.mapper.StockTakeMapper;
import com.yawei.erp.mapper.SysMapper;
import com.yawei.erp.mapper.TradeMapper;

/** 库存盘点：录入实盘数 → 确认后按差异调整库存（move_type=Constants.MOVE_ADJUST 流水，盘盈+盘亏-）
 *  期初库存 = 第一次盘点录入实盘数即建立 */
@RestController
@RequestMapping("/api/stock-takes")
public class StockTakeController {

    private final StockTakeMapper mapper;
    private final SysMapper sys;
    private final TradeMapper trade;
    private final MaterialMapper materials;
    private final SequenceUtil seq;

    public StockTakeController(StockTakeMapper mapper, SysMapper sys, TradeMapper trade,
                               MaterialMapper materials, SequenceUtil seq) {
        this.mapper = mapper;
        this.sys = sys;
        this.trade = trade;
        this.materials = materials;
        this.seq = seq;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.takeList(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        var rows = mapper.takeExists(id);
        if (rows.isEmpty()) {
            return ApiResponse.fail("盘点单不存在");
        }
        return ApiResponse.ok(Map.of("main", rows.get(0), "items", mapper.takeItems(id)));
    }

    /** 创建盘点单（草稿）：明细只需 materialId + actualQty；账面库存/差异服务端自动计算 */
    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody TakeReq req) {
        if (req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请添加盘点明细");
        }
        String date = req.takeDate() == null || req.takeDate().isBlank() ? today() : req.takeDate();
        String stNo = seq.nextDaily(Constants.SEQ_STOCK_TAKE);
        BigDecimal totalDiffQty = BigDecimal.ZERO;
        BigDecimal totalDiffAmt = BigDecimal.ZERO;
        int totalItems = 0;
        mapper.takeInsert(stNo, date, req.warehouseId(), "draft", 0, BigDecimal.ZERO, BigDecimal.ZERO, req.remark());
        Long stId = mapper.lastTakeId(stNo);
        for (var it : req.items()) {
            if (it.materialId() == null) continue;
            Map<String, Object> m = materialInfo(it.materialId());
            String mname = m == null ? "" : String.valueOf(m.getOrDefault("name", ""));
            String spec = m == null ? "" : String.valueOf(m.getOrDefault("spec", ""));
            String unit = m == null ? "" : String.valueOf(m.getOrDefault("unit", ""));
            BigDecimal cost = m == null ? BigDecimal.ZERO : (BigDecimal) m.getOrDefault("cost", BigDecimal.ZERO);
            BigDecimal book = stock(it.materialId());
            BigDecimal actual = it.actualQty() == null ? book : it.actualQty();
            BigDecimal diff = actual.subtract(book);
            BigDecimal diffAmt = diff.multiply(cost).setScale(2, RoundingMode.HALF_UP);
            mapper.takeItemInsert(stId, it.materialId(), mname, spec, unit, book, actual, diff, cost, diffAmt);
            totalItems++;
            totalDiffQty = totalDiffQty.add(diff);
            totalDiffAmt = totalDiffAmt.add(diffAmt);
        }
        mapper.takeUpdateStatus(stId, "draft");
        // 更新汇总
        updateTotals(stId, totalItems, totalDiffQty, totalDiffAmt);
        return ApiResponse.ok(Map.of("id", stId, "stNo", stNo));
    }

    /** 确认盘点：按差异生成调整流水（盘盈+ / 盘亏-），状态→done */
    @PostMapping("/{id}/confirm")
    @Transactional
    public Map<String, Object> confirm(@PathVariable Long id) {
        var rows = mapper.takeExists(id);
        if (rows.isEmpty()) {
            return ApiResponse.fail("盘点单不存在");
        }
        Map<String, Object> main = rows.get(0);
        if ("done".equals(String.valueOf(main.get("status")))) {
            return ApiResponse.fail("该盘点单已确认，不能重复确认");
        }
        String stNo = String.valueOf(main.get("st_no"));
        String date = String.valueOf(main.get("take_date"));
        Long whId = main.get("warehouse_id") == null ? 1L : ((Number) main.get("warehouse_id")).longValue();
        BigDecimal totalDiffQty = BigDecimal.ZERO;
        BigDecimal totalDiffAmt = BigDecimal.ZERO;
        for (var it : mapper.takeItems(id)) {
            BigDecimal diff = it.get("diff_qty") == null ? BigDecimal.ZERO : (BigDecimal) it.get("diff_qty");
            BigDecimal cost = it.get("unit_cost") == null ? BigDecimal.ZERO : (BigDecimal) it.get("unit_cost");
            BigDecimal book = it.get("book_qty") == null ? BigDecimal.ZERO : (BigDecimal) it.get("book_qty");
            BigDecimal actual = it.get("actual_qty") == null ? BigDecimal.ZERO : (BigDecimal) it.get("actual_qty");
            BigDecimal diffAmt = diff.multiply(cost).setScale(2, RoundingMode.HALF_UP);
            if (diff.compareTo(BigDecimal.ZERO) != 0) {
                Long materialId = ((Number) it.get("material_id")).longValue();
                trade.movementInsert(materialId, "adjust", "stock_take", id,
                        diff, book, actual, cost, diffAmt, date,
                        "盘点#" + stNo + (diff.signum() > 0 ? "盘盈" : "盘亏"));
            }
            totalDiffQty = totalDiffQty.add(diff);
            totalDiffAmt = totalDiffAmt.add(diffAmt);
        }
        mapper.takeUpdateStatus(id, "done");
        updateTotals(id, ((Number) main.getOrDefault("total_items", 0)).intValue(), totalDiffQty, totalDiffAmt);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.takeExists(id).isEmpty()) {
            return ApiResponse.fail("盘点单不存在");
        }
        mapper.takeItemsDelete(id);
        trade.movementsDeleteByRef("stock_take", id);
        mapper.takeDelete(id);
        return ApiResponse.ok();
    }

    private void updateTotals(Long stId, int items, BigDecimal diffQty, BigDecimal diffAmt) {
        mapper.takeUpdateTotals(stId, items, diffQty, diffAmt);
    }

    private Map<String, Object> materialInfo(Long materialId) {
        Material m = materials.selectById(materialId);
        if (m == null) return null;
        return Map.of("name", m.getName() == null ? "" : m.getName(),
                "spec", m.getSpec() == null ? "" : m.getSpec(),
                "unit", m.getUnit() == null ? "" : m.getUnit(),
                "cost", m.getPurchasePrice() == null ? BigDecimal.ZERO : m.getPurchasePrice());
    }

    private BigDecimal stock(Long materialId) {
        BigDecimal v = sys.currentStock(materialId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private String today() {
        return LocalDate.now().toString();
    }

    public record TakeReq(Long warehouseId, String takeDate, String remark, List<TakeItemReq> items) {}
    public record TakeItemReq(Long materialId, BigDecimal actualQty) {}
}
