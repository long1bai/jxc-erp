package com.jxc.erp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxc.erp.common.Constants;
import com.jxc.erp.dto.ProductionDtos.PiReq;
import com.jxc.erp.dto.ProductionDtos.PrtReq;
import com.jxc.erp.entity.Material;
import com.jxc.erp.mapper.MaterialMapper;
import com.jxc.erp.mapper.ProductionMapper;
import com.jxc.erp.mapper.SysMapper;
import com.jxc.erp.mapper.TradeMapper;
import com.jxc.erp.util.SequenceUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 生产管理业务层：成品入库、生产退料、统计。
 * 逻辑自 ProductionController 抽取（2026-08-02 企业化分层重构），行为保持不变。
 */
@Service
public class ProductionService {

    private final ProductionMapper mapper;
    private final SysMapper sys;
    private final TradeMapper trade;
    private final MaterialMapper materials;
    private final SequenceUtil seq;

    public ProductionService(ProductionMapper mapper, SysMapper sys, TradeMapper trade,
                             MaterialMapper materials, SequenceUtil seq) {
        this.mapper = mapper;
        this.sys = sys;
        this.trade = trade;
        this.materials = materials;
        this.seq = seq;
    }

    // ============ 成品入库 ============

    public Map<String, Object> piList(String keyword, int page, int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.piList(keyword.trim(), p);
        return Map.of("items", items, "total", p.getTotal(), "page", p.getCurrent(), "size", p.getSize());
    }

    public Map<String, Object> piDetail(Long id) {
        var rows = mapper.piExists(id);
        if (rows.isEmpty()) {
            return null;
        }
        return Map.of("main", rows.get(0), "items", mapper.piItems(id));
    }

    @Transactional
    public Map<String, Object> piCreate(PiReq req) {
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
            return Map.of("error", "没有有效的产品明细");
        }
        mapper.piSummaryUpdate(piId, totalQty, totalItems);
        return Map.of("id", piId, "piNo", piNo);
    }

    @Transactional
    public boolean piDelete(Long id) {
        if (mapper.piExists(id).isEmpty()) {
            return false;
        }
        mapper.piItemsDelete(id);
        trade.movementsDeleteByRef("production_in", id);
        mapper.piDelete(id);
        return true;
    }

    // ============ 生产退料 ============

    public Map<String, Object> prtList(String keyword, int page, int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.prtList(keyword.trim(), p);
        return Map.of("items", items, "total", p.getTotal(), "page", p.getCurrent(), "size", p.getSize());
    }

    public Map<String, Object> prtDetail(Long id) {
        var rows = mapper.prtExists(id);
        if (rows.isEmpty()) {
            return null;
        }
        return Map.of("main", rows.get(0), "items", mapper.prtItems(id));
    }

    @Transactional
    public Map<String, Object> prtCreate(PrtReq req) {
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
        return Map.of("id", prtId, "prtNo", prtNo);
    }

    @Transactional
    public boolean prtDelete(Long id) {
        if (mapper.prtExists(id).isEmpty()) {
            return false;
        }
        mapper.prtItemsDelete(id);
        trade.movementsDeleteByRef("production_return", id);
        mapper.prtDelete(id);
        return true;
    }

    // ============ 统计 ============

    public List<Map<String, Object>> inStats(String from, String to) {
        return mapper.productionInStats(from, to);
    }

    public List<Map<String, Object>> returnStats(String from, String to) {
        return mapper.productionReturnStats(from, to);
    }

    private BigDecimal stock(Long materialId) {
        BigDecimal v = sys.currentStock(materialId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private String today() {
        return LocalDate.now().toString();
    }
}
