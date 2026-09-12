package com.jxc.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.common.PageResult;
import com.jxc.erp.util.SequenceUtil;
import com.jxc.erp.mapper.ApprovalMapper;
import com.jxc.erp.mapper.SysMapper;
import com.jxc.erp.mapper.TradeMapper;
import com.jxc.erp.util.MoneyUtils;

/** 送货单（销售出库：关联订单出货或临时出货，扣库存，回写订单状态） */
@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final TradeMapper mapper;
    private final SysMapper sys;
    private final SequenceUtil seq;

    @org.springframework.beans.factory.annotation.Autowired
    private ApprovalMapper approvalMapper;

    public DeliveryController(TradeMapper mapper, SysMapper sys, SequenceUtil seq) {
        this.mapper = mapper;
        this.sys = sys;
        this.seq = seq;
    }

    public record ItemReq(Long materialId, String materialName, String spec, String unit,
                          BigDecimal quantity, BigDecimal unitPrice) {}

    public record CreateReq(Long customerId, Long orderId, String deliveryDate, String remark,
                            String handler, List<ItemReq> items) {}

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.deliveryList(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        var main = mapper.deliveryDetail(id);
        if (main.isEmpty()) {
            return ApiResponse.fail("送货单不存在");
        }
        return ApiResponse.ok(Map.of("main", main.get(0), "items", mapper.deliveryItems(id)));
    }

    @GetMapping("/open-orders")
    public Map<String, Object> openOrders(@RequestParam(required = false) Long customerId) {
        return ApiResponse.ok(Map.of("items", mapper.openOrders(customerId)));
    }

    @PostMapping
    @Transactional
    public Map<String, Object> create(@RequestBody CreateReq req) {
        if (req.customerId() == null || req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请选择客户并添加明细");
        }
        Long orderId = req.orderId();
        if (orderId != null) {
            var om = mapper.orderDetail(orderId);
            if (om.isEmpty()) {
                return ApiResponse.fail("关联订单不存在");
            }
            if ("cancelled".equals(om.get(0).get("status"))) {
                return ApiResponse.fail("订单已取消，不能出货");
            }
            for (var it : req.items()) {
                var row = mapper.orderItemDelivered(orderId, it.materialId());
                if (row.isEmpty()) {
                    return ApiResponse.fail("物料「" + it.materialName() + "」不在订单明细中");
                }
                BigDecimal ordered = (BigDecimal) row.get(0).get("quantity");
                BigDecimal delivered = (BigDecimal) row.get(0).get("delivered_quantity");
                BigDecimal remain = ordered.subtract(delivered == null ? BigDecimal.ZERO : delivered);
                if (it.quantity().compareTo(remain) > 0) {
                    return ApiResponse.fail("物料「" + it.materialName() + "」出货 " + it.quantity() +
                            " 超过订单剩余 " + remain);
                }
            }
        }

        String dnNo = seq.nextDaily("SH-XSDD");
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
        mapper.deliveryInsert(dnNo, req.customerId(), cname == null ? "" : cname, orderId,
                req.deliveryDate() == null || req.deliveryDate().isBlank() ? today() : req.deliveryDate(),
                req.handler(), totalQty, totalAmt, req.remark());
        Long dnId = mapper.lastDeliveryId(dnNo);
        int sort = 0;
        for (var it : req.items()) {
            if (it.materialId() == null || it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            BigDecimal amt = MoneyUtils.amount(it.quantity(), it.unitPrice());
            mapper.deliveryItemInsert(dnId, it.materialId(), it.materialName(), it.spec(), it.unit(),
                    it.quantity(), it.unitPrice(), amt, sort++);
        }
        // 审批流：启用审批时创建为 pending 且不执行库存动作（明细已存），审批通过后由 ApprovalController 扣库存
        if (approvalMapper.approvalEnabled("delivery") == 1) {
            approvalMapper.updateDeliveryApprove(dnId, "pending", "");
            return ApiResponse.ok(Map.of("id", dnId, "dnNo", dnNo, "pendingApproval", true));
        }
        for (var it : req.items()) {
            if (it.materialId() == null || it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            BigDecimal before = currentStock(it.materialId());
            BigDecimal after = before.subtract(it.quantity());
            mapper.movementInsert(it.materialId(), "out", "delivery", dnId,
                    it.quantity(), before, after, it.unitPrice(), MoneyUtils.amount(it.quantity(), it.unitPrice()), today(), "销售出货#" + dnNo);
        }
        if (orderId != null) {
            recalcOrder(orderId);
        }
        return ApiResponse.ok(Map.of("id", dnId, "dnNo", dnNo));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.deliverySettledCount(id) > 0) {
            return ApiResponse.fail("该送货单已核销收款，不能删除");
        }
        var rows = mapper.deliveryOrderRef(id);
        if (rows.isEmpty()) {
            return ApiResponse.fail("送货单不存在");
        }
        Object orderId = rows.get(0).get("customer_order_id");
        // 外键约束：先删明细 → 流水 → 主单
        mapper.deliveryItemsDelete(id);
        mapper.movementsDeleteByRef("delivery", id);
        mapper.deliveryDelete(id);
        if (orderId != null) {
            recalcOrder(((Number) orderId).longValue());
        }
        return ApiResponse.ok();
    }

    /** 重算订单已送数量/金额与状态（主表 + 明细 delivered_quantity 同步） */
    private void recalcOrder(Long orderId) {
        Map<String, Object> agg = mapper.deliveryAgg(orderId);
        BigDecimal qty = (BigDecimal) agg.get("qty");
        BigDecimal amt = (BigDecimal) agg.get("amt");
        var om = mapper.orderTotal(orderId);
        BigDecimal total = om.isEmpty() ? BigDecimal.ZERO : (BigDecimal) om.get(0).get("total_quantity");
        String status;
        if (total.signum() == 0 || qty.compareTo(total) >= 0) {
            status = "done";
        } else if (qty.signum() > 0) {
            status = "partial";
        } else {
            status = "pending";
        }
        mapper.orderUpdateDelivered(orderId, qty, amt, status);
        mapper.orderItemsSyncDelivered(orderId);
    }

    private BigDecimal currentStock(Long materialId) {
        BigDecimal v = sys.currentStock(materialId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private String today() {
        return java.time.LocalDate.now().toString();
    }
}
