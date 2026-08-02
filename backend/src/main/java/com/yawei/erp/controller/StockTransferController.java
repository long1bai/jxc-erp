package com.yawei.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.yawei.erp.common.BaseController;
import com.yawei.erp.common.Constants;
import com.yawei.erp.security.SessionStore;
import com.yawei.erp.util.SequenceUtil;
import com.yawei.erp.entity.Material;
import com.yawei.erp.entity.Warehouse;
import com.yawei.erp.mapper.MaterialMapper;
import com.yawei.erp.mapper.StockTransferMapper;
import com.yawei.erp.mapper.TradeMapper;
import com.yawei.erp.mapper.WarehouseMapper;
import com.yawei.erp.security.TokenUtils;

/**
 * 库存调拨单：仓库间调拨物料
 * 设计：创建即生效（出库流水 out + 入库流水 in，ref_type='transfer'）；删除回滚流水
 */
@RestController
@RequestMapping("/api/stock/transfers")
public class StockTransferController extends BaseController {

    private final StockTransferMapper mapper;
    private final TradeMapper trade;
    private final MaterialMapper materials;
    private final WarehouseMapper warehouses;
    private final SequenceUtil seq;
    private final SessionStore sessionStore;

    public StockTransferController(StockTransferMapper mapper, TradeMapper trade, MaterialMapper materials,
                                   WarehouseMapper warehouses, SequenceUtil seq, SessionStore sessionStore) {
        this.mapper = mapper;
        this.trade = trade;
        this.materials = materials;
        this.warehouses = warehouses;
        this.seq = seq;
        this.sessionStore = sessionStore;
    }

    /** 调拨请求体 */
    public record TransferReq(Long fromWarehouseId, Long toWarehouseId, String transferDate,
                              String remark, List<Item> items) {
        public record Item(Long materialId, BigDecimal quantity) {
        }
    }

    /** 创建调拨单（事务：单 + 明细 + 出/入库双流水，库存实时变动） */
    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody TransferReq req,
                                      @RequestHeader(value = "Authorization", required = false) String auth) {
        if (req.fromWarehouseId() == null || req.toWarehouseId() == null
                || req.fromWarehouseId().equals(req.toWarehouseId())) {
            return fail("请选择两个不同的仓库");
        }
        if (req.items() == null || req.items().isEmpty()) {
            return fail("请添加调拨明细");
        }
        String date = req.transferDate() == null || req.transferDate().isBlank() ? LocalDate.now().toString() : req.transferDate();
        String no = seq.nextDaily(Constants.SEQ_STOCK_TRANSFER);
        String fromName = warehouseName(req.fromWarehouseId());
        String toName = warehouseName(req.toWarehouseId());
        var user = sessionStore.verify(TokenUtils.extractToken(auth));
        String createdBy = user == null ? "" : user.username();

        mapper.insert(no, req.fromWarehouseId(), fromName, req.toWarehouseId(), toName, date,
                req.remark() == null ? "" : req.remark(), createdBy);
        Long id = mapper.lastId(no);

        BigDecimal totalQty = BigDecimal.ZERO;
        int n = 0;
        for (var it : req.items()) {
            if (it.materialId() == null || it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            Map<String, Object> m = materialInfo(it.materialId());
            if (m == null) {
                continue;
            }
            String mname = String.valueOf(m.getOrDefault("name", ""));
            String spec = String.valueOf(m.getOrDefault("spec", ""));
            String unit = String.valueOf(m.getOrDefault("unit", ""));
            BigDecimal price = (BigDecimal) m.getOrDefault("cost", BigDecimal.ZERO);
            BigDecimal amount = it.quantity().multiply(price).setScale(2, java.math.RoundingMode.HALF_UP);

            mapper.itemInsert(id, it.materialId(), mname, spec, unit, it.quantity());

            // 出库流水（调出仓 -）
            BigDecimal fromStock = mapper.stockByWarehouse(it.materialId(), req.fromWarehouseId());
            trade.movementInsertWh(it.materialId(), req.fromWarehouseId(), Constants.MOVE_OUT, "transfer", id,
                    it.quantity(), fromStock, fromStock.subtract(it.quantity()), price, amount, date,
                    "调拨出库#" + no);
            // 入库流水（调入仓 +）
            BigDecimal toStock = mapper.stockByWarehouse(it.materialId(), req.toWarehouseId());
            trade.movementInsertWh(it.materialId(), req.toWarehouseId(), Constants.MOVE_IN, "transfer", id,
                    it.quantity(), toStock, toStock.add(it.quantity()), price, amount, date,
                    "调拨入库#" + no);

            totalQty = totalQty.add(it.quantity());
            n++;
        }
        if (n == 0) {
            throw new RuntimeException("没有有效的调拨明细");
        }
        mapper.updateTotals(id, n, totalQty);
        return ok(Map.of("id", id, "transferNo", no));
    }

    /** 调拨单列表（分页 + 单号/仓库/日期筛选） */
    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "") String kw,
                                    @RequestParam(defaultValue = "") String start,
                                    @RequestParam(defaultValue = "") String end,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = pageParams(page, size);
        List<Map<String, Object>> items = mapper.query(kw.trim(), start, end, p);
        return pageResult(items, p.getTotal(), p.getCurrent(), p.getSize());
    }

    /** 调拨单详情（单 + 明细） */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        Map<String, Object> main = mapper.byId(id);
        if (main == null) {
            return fail("调拨单不存在");
        }
        return ok(Map.of("main", main, "items", mapper.items(id)));
    }

    /** 删除调拨单（软删单 + 删明细 + 回滚流水，库存恢复） */
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        mapper.delete(id);
        mapper.itemsDelete(id);
        trade.movementsDeleteByRef("transfer", id);
        return ok();
    }

    private String warehouseName(Long wid) {
        Warehouse w = warehouses.selectById(wid);
        return w == null ? "" : w.getName();
    }

    private Map<String, Object> materialInfo(Long materialId) {
        Material m = materials.selectById(materialId);
        if (m == null) {
            return null;
        }
        return Map.of("name", String.valueOf(m.getName()),
                "spec", m.getSpec() == null ? "" : m.getSpec(),
                "unit", m.getUnit() == null ? "" : m.getUnit(),
                "cost", m.getPurchasePrice() == null ? BigDecimal.ZERO : m.getPurchasePrice());
    }
}
