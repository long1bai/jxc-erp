package com.jxc.erp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jxc.erp.dto.PoOrderDtos.CreateReq;
import com.jxc.erp.dto.PoOrderDtos.ReceiveReq;
import com.jxc.erp.mapper.ApprovalMapper;
import com.jxc.erp.mapper.SysMapper;
import com.jxc.erp.mapper.TradeMapper;
import com.jxc.erp.util.SequenceUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 独立采购订单业务层（订单 → 分批入库 → 执行跟踪）。
 * 逻辑自 PoOrderController 抽取（2026-08-02 企业化分层重构），行为保持不变。
 */
@Service
public class PoOrderService {

    private final TradeMapper mapper;
    private final SysMapper sys;
    private final SequenceUtil seq;
    private final ApprovalMapper approvalMapper;

        private final SysConfigService cfg;

public PoOrderService(TradeMapper mapper, SysMapper sys, SequenceUtil seq, ApprovalMapper approvalMapper, SysConfigService cfg) {
        this.mapper = mapper;
        this.cfg = cfg;
        this.sys = sys;
        this.seq = seq;
        this.approvalMapper = approvalMapper;
    }

    /** 创建采购订单 */
    @Transactional
    public Map<String, Object> create(CreateReq req) {
        String no = seq.nextDaily(cfg.get("seq_po"));
        String sname = sys.supplierName(req.supplierId());
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (var it : req.items()) {
            totalQty = totalQty.add(it.quantity() == null ? BigDecimal.ZERO : it.quantity());
            totalAmt = totalAmt.add(amount(it.quantity(), it.unitPrice()));
        }
        String date = req.orderDate() == null || req.orderDate().isBlank() ? today() : req.orderDate();
        mapper.poOrderInsert(no, req.supplierId(), sname == null ? "" : sname, date, req.handler(), req.remark(),
                totalQty, totalAmt);
        Long id = mapper.lastPoOrderId(no);
        int sort = 0;
        for (var it : req.items()) {
            if (it.materialId() == null || it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            mapper.poOrderItemInsert(id, it.materialId(), it.materialName(), it.spec(), it.unit(),
                    it.quantity(), it.unitPrice() == null ? BigDecimal.ZERO : it.unitPrice(),
                    amount(it.quantity(), it.unitPrice()), sort++);
        }
        return Map.of("id", id, "poOrderNo", no);
    }

    /** 订单列表（keyword 搜单号/供应商，status 过滤） */
    public Map<String, Object> list(String keyword, String status, int page, int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.poOrderList(keyword.trim(), status.trim(), p);
        return Map.of("items", items, "total", p.getTotal(), "page", p.getCurrent(), "size", p.getSize());
    }

    /** 订单详情（含明细 + 已收数量） */
    public Map<String, Object> detail(Long id) {
        var rows = mapper.poOrderDetail(id);
        if (rows.isEmpty()) {
            return null;
        }
        return Map.of("main", rows.get(0), "items", mapper.poOrderItems(id));
    }

    /** 按订单入库（分批）：生成入库单 + 加库存 + 更新订单执行状态 */
    @Transactional
    public Map<String, Object> receive(Long id, List<ReceiveReq> items) {
        var main = mapper.poOrderDetail(id);
        if (main.isEmpty()) {
            return Map.of("error", "采购订单不存在");
        }
        var order = main.get(0);
        String status = String.valueOf(order.get("status"));
        if ("done".equals(status)) {
            return Map.of("error", "该订单已全部入库");
        }
        // 校验本次入库数量 ≤ 剩余未收数量
        var orderItems = mapper.poOrderItems(id);
        Map<Long, BigDecimal> remaining = new java.util.HashMap<>();
        for (var oi : orderItems) {
            Long mid = ((Number) oi.get("material_id")).longValue();
            BigDecimal qty = (BigDecimal) oi.get("quantity");
            BigDecimal received = oi.get("received_quantity") == null ? BigDecimal.ZERO : (BigDecimal) oi.get("received_quantity");
            remaining.put(mid, qty.subtract(received));
        }
        for (var it : items) {
            BigDecimal rem = remaining.getOrDefault(it.materialId(), BigDecimal.ZERO);
            if (it.quantity() == null || it.quantity().signum() <= 0) {
                return Map.of("error", "入库数量必须大于 0");
            }
            if (it.quantity().compareTo(rem) > 0) {
                return Map.of("error", "物料 #" + it.materialId() + " 本次入库超过剩余未收数量（剩余 " + rem + "）");
            }
        }
        // 生成入库单（复用现有 purchase 流程：主单+明细+库存流水）
        String poNo = seq.nextDaily(cfg.get("seq_cgdd"));
        Long whId = cfg.getLong("default_warehouse", 1L);
        var orderMain = main.get(0);
        BigDecimal rcvQty = BigDecimal.ZERO;
        BigDecimal rcvAmt = BigDecimal.ZERO;
        for (var it : items) {
            BigDecimal qty = it.quantity();
            var oi = orderItems.stream()
                    .filter(x -> ((Number) x.get("material_id")).longValue() == it.materialId())
                    .findFirst().orElse(null);
            BigDecimal price = oi == null || oi.get("unit_price") == null ? BigDecimal.ZERO : (BigDecimal) oi.get("unit_price");
            rcvQty = rcvQty.add(qty);
            rcvAmt = rcvAmt.add(qty.multiply(price));
        }
        mapper.purchaseInsert(poNo, (Long) order.get("supplier_id"), null, "", today(), String.valueOf(order.get("handler") == null ? "" : order.get("handler")),
                rcvQty, rcvAmt, "采购订单入库#" + order.get("po_order_no"));
        Long poId = mapper.lastPurchaseId(poNo);
        mapper.linkPoOrder(poId, id);
        int sort = 0;
        for (var it : items) {
            var oi = orderItems.stream()
                    .filter(x -> ((Number) x.get("material_id")).longValue() == it.materialId())
                    .findFirst().orElse(null);
            BigDecimal price = oi == null || oi.get("unit_price") == null ? BigDecimal.ZERO : (BigDecimal) oi.get("unit_price");
            BigDecimal amt = it.quantity().multiply(price);
            String mname = oi == null ? "" : String.valueOf(oi.get("material_name"));
            String spec = oi == null ? "" : String.valueOf(oi.get("spec") == null ? "" : oi.get("spec"));
            String unit = oi == null ? "" : String.valueOf(oi.get("unit") == null ? "" : oi.get("unit"));
            mapper.purchaseItemInsert(poId, it.materialId(), mname, spec, unit, "", it.quantity(), price, amt, sort++);
            BigDecimal before = currentStock(it.materialId());
            BigDecimal after = before.add(it.quantity());
            mapper.movementInsertWh(it.materialId(), whId, "in", "purchase", poId,
                    it.quantity(), before, after, price, amt, today(), "采购订单入库#" + poNo);
        }
        // 更新订单执行状态：累加已收数量，全收完 → done，否则 partial
        boolean allDone = true;
        for (var oi : orderItems) {
            Long mid = ((Number) oi.get("material_id")).longValue();
            BigDecimal qty = (BigDecimal) oi.get("quantity");
            BigDecimal received = oi.get("received_quantity") == null ? BigDecimal.ZERO : (BigDecimal) oi.get("received_quantity");
            BigDecimal thisRcv = items.stream()
                    .filter(x -> x.materialId().equals(mid))
                    .map(ReceiveReq::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal newReceived = received.add(thisRcv);
            mapper.poOrderItemReceived(oi.get("id"), newReceived);
            if (newReceived.compareTo(qty) < 0) {
                allDone = false;
            }
        }
        mapper.poOrderStatus(id, allDone ? "done" : "partial");
        return Map.of("poId", poId, "poNo", poNo, "orderStatus", allDone ? "done" : "partial");
    }

    /** 删除订单（仅未入库或部分入库未完成时允许；有入库关联的禁止） */
    @Transactional
    public Map<String, Object> delete(Long id) {
        Long linked = mapper.poOrderReceivedCount(id);
        if (linked != null && linked > 0) {
            return Map.of("error", "该订单已有入库单，不能删除（如需作废请删除对应入库单）");
        }
        mapper.poOrderItemsDelete(id);
        mapper.poOrderDelete(id);
        return Map.of();
    }

    private BigDecimal amount(BigDecimal qty, BigDecimal price) {
        return qty == null ? BigDecimal.ZERO : qty.multiply(price == null ? BigDecimal.ZERO : price);
    }

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private BigDecimal currentStock(Long materialId) {
        return approvalMapper.currentStock(materialId);
    }
}
