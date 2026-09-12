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
import com.jxc.erp.common.Constants;
import com.jxc.erp.common.PageResult;
import com.jxc.erp.util.SequenceUtil;
import com.jxc.erp.mapper.SalesReturnMapper;
import com.jxc.erp.mapper.SysMapper;
import com.jxc.erp.mapper.TradeMapper;

/** 销售退货：客户退货回来，自动加回库存（流水 in）；删除=逻辑删，流水标记 deleted 库存自动回退 */
@RestController
@RequestMapping("/api/sales-returns")
public class SalesReturnController {

    private final SalesReturnMapper mapper;
    private final SysMapper sys;
    private final TradeMapper trade;
    private final SequenceUtil seq;

    public SalesReturnController(SalesReturnMapper mapper, SysMapper sys, TradeMapper trade, SequenceUtil seq) {
        this.mapper = mapper;
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
        if (req.customerId() == null || req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请选择客户并填写退货明细");
        }
        String cname = sys.customerName(req.customerId());
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (var it : req.items()) {
            totalQty = totalQty.add(it.quantity());
            totalAmt = totalAmt.add(it.quantity().multiply(it.unitPrice() == null ? BigDecimal.ZERO : it.unitPrice()));
        }
        String date = req.returnDate() == null || req.returnDate().isBlank() ? today() : req.returnDate();
        String srNo = seq.nextDaily(Constants.SEQ_SALES_RETURN);
        mapper.returnInsert(srNo, req.customerId(), cname == null ? "" : cname, date,
                req.orderId(), totalQty, totalAmt.setScale(2, RoundingMode.HALF_UP), req.remark());
        Long srId = mapper.lastReturnId(srNo);
        int idx = 0;
        for (var it : req.items()) {
            BigDecimal amt = it.quantity().multiply(it.unitPrice() == null ? BigDecimal.ZERO : it.unitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            mapper.returnItemInsert(srId, it.materialId(), it.materialName() == null ? "" : it.materialName(),
                    it.spec() == null ? "" : it.spec(), it.unit() == null ? "" : it.unit(),
                    it.quantity(), it.unitPrice() == null ? BigDecimal.ZERO : it.unitPrice(), amt, idx++);
            if (it.materialId() != null) {
                BigDecimal before = stock(it.materialId());
                BigDecimal after = before.add(it.quantity());
                trade.movementInsert(it.materialId(), "in", "sales_return", srId,
                        it.quantity(), before, after,
                        it.unitPrice() == null ? BigDecimal.ZERO : it.unitPrice(), amt, date, "退货#" + srNo + "入库");
            }
        }
        return ApiResponse.ok(Map.of("id", srId, "srNo", srNo));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.returnExists(id).isEmpty()) {
            return ApiResponse.fail("退货单不存在");
        }
        mapper.returnItemsDelete(id);
        trade.movementsDeleteByRef("sales_return", id);
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

    public record ReturnReq(Long customerId, String returnDate, Long orderId, String remark,
                            List<ReturnItemReq> items) {}
    public record ReturnItemReq(Long materialId, String materialName, String spec, String unit,
                                BigDecimal quantity, BigDecimal unitPrice) {}
}
