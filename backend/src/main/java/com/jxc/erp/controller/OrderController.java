package com.jxc.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.common.Constants;
import com.jxc.erp.common.PageResult;
import com.jxc.erp.util.SequenceUtil;
import com.jxc.erp.mapper.SysMapper;
import com.jxc.erp.mapper.TradeMapper;
import com.jxc.erp.util.MoneyUtils;

/** 客户订单（销售下单，出货由送货单完成） */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final TradeMapper mapper;
    private final SysMapper sys;
    private final SequenceUtil seq;

    public OrderController(TradeMapper mapper, SysMapper sys, SequenceUtil seq) {
        this.mapper = mapper;
        this.sys = sys;
        this.seq = seq;
    }

    public record ItemReq(Long materialId, String materialName, String spec, String unit,
                          BigDecimal quantity, BigDecimal unitPrice) {}

    public record CreateReq(Long customerId, String orderDate, String remark, List<ItemReq> items) {}

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.orderList(keyword.trim(), status, p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        var main = mapper.orderDetail(id);
        if (main.isEmpty()) {
            return ApiResponse.fail("订单不存在");
        }
        return ApiResponse.ok(Map.of("main", main.get(0), "items", mapper.orderItems(id)));
    }

    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody CreateReq req) {
        if (req.customerId() == null || req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请选择客户并添加明细");
        }
        String coNo = seq.nextDaily(Constants.SEQ_SALES_ORDER);
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (var it : req.items()) {
            if (it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            totalQty = totalQty.add(it.quantity());
            totalAmt = totalAmt.add(MoneyUtils.amount(it.quantity(), it.unitPrice()));
        }
        String cname = sys.customerName(req.customerId());
        mapper.orderInsert(coNo, req.customerId(), cname == null ? "" : cname,
                req.orderDate() == null || req.orderDate().isBlank() ? today() : req.orderDate(),
                totalQty, totalAmt, req.remark());
        Long coId = mapper.lastOrderId(coNo);
        int sort = 0;
        for (var it : req.items()) {
            if (it.materialId() == null || it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            mapper.orderItemInsert(coId, it.materialId(), it.materialName(), it.spec(), it.unit(),
                    it.quantity(), it.unitPrice(), MoneyUtils.amount(it.quantity(), it.unitPrice()), sort++);
        }
        return ApiResponse.ok(Map.of("id", coId, "coNo", coNo));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        var rows = mapper.orderStatus(id);
        if (rows.isEmpty()) {
            return ApiResponse.fail("订单不存在");
        }
        BigDecimal delivered = (BigDecimal) rows.get(0).get("delivered_quantity");
        if (delivered != null && delivered.signum() > 0) {
            return ApiResponse.fail("订单已出货，不能删除");
        }
        if ("done".equals(rows.get(0).get("status"))) {
            return ApiResponse.fail("订单已出货，不能删除");
        }
        mapper.orderItemsDelete(id);
        mapper.orderDelete(id);
        return ApiResponse.ok();
    }


    private String today() {
        return java.time.LocalDate.now().toString();
    }
}
