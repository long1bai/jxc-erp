package com.jxc.erp.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.security.SessionStore;
import com.jxc.erp.mapper.ApprovalMapper;
import com.jxc.erp.security.TokenUtils;

/** 单据审批流（可插拔：approval_config 表控制，默认关闭）
 *  启用审批的单据：创建时 approve_status=pending 且不执行库存动作；
 *  审批通过 → 执行库存动作（采购加库存/送货扣库存）+ approved；
 *  审批驳回 → rejected，不动库存。 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    @Autowired
    private ApprovalMapper ap;

    @Autowired
    private SessionStore sessionStore;

    /** 待审批列表 */
    @GetMapping("/pending")
    public Map<String, Object> pending() {
        return ApiResponse.ok(Map.of(
                "purchases", ap.pendingPurchases(),
                "deliveries", ap.pendingDeliveries()));
    }

    /** 审批开关配置 */
    @GetMapping("/config")
    public Map<String, Object> config() {
        return ApiResponse.ok(Map.of(
                "purchase", ap.approvalEnabled("purchase") == 1,
                "delivery", ap.approvalEnabled("delivery") == 1));
    }

    /** 保存审批开关 */
    @PostMapping("/config")
    @Transactional
    public Map<String, Object> saveConfig(@RequestBody ConfigReq req) {
        ap.upsertConfig(178550000000000001L, "purchase", req.purchase() ? 1 : 0);
        ap.upsertConfig(178550000000000002L, "delivery", req.delivery() ? 1 : 0);
        return ApiResponse.ok();
    }

    public record ConfigReq(boolean purchase, boolean delivery) {}

    /** 审批通过：执行库存动作 + 状态 approved */
    @PostMapping("/{type}/{id}/approve")
    @Transactional
    public Map<String, Object> approve(@PathVariable String type, @PathVariable Long id,
                                       @RequestHeader(value = "Authorization", required = false) String auth) {
        String by = currentUser(auth);
        if ("purchase".equals(type)) {
            if (ap.updatePurchaseApprove(id, "approved", by) == 0) return ApiResponse.fail("采购单不存在或已被处理");
            applyPurchaseStock(id);
        } else if ("delivery".equals(type)) {
            if (ap.updateDeliveryApprove(id, "approved", by) == 0) return ApiResponse.fail("送货单不存在或已被处理");
            applyDeliveryStock(id);
        } else {
            return ApiResponse.fail("不支持的单据类型: " + type);
        }
        return ApiResponse.ok();
    }

    /** 审批驳回（不动库存） */
    @PostMapping("/{type}/{id}/reject")
    @Transactional
    public Map<String, Object> reject(@PathVariable String type, @PathVariable Long id,
                                      @RequestHeader(value = "Authorization", required = false) String auth) {
        String by = currentUser(auth);
        int n = "purchase".equals(type) ? ap.updatePurchaseApprove(id, "rejected", by)
                : "delivery".equals(type) ? ap.updateDeliveryApprove(id, "rejected", by) : 0;
        if (n == 0) return ApiResponse.fail("单据不存在或已被处理");
        return ApiResponse.ok();
    }

    /** 审批通过后：采购加库存（读明细写流水，与创建逻辑一致） */
    private void applyPurchaseStock(Long poId) {
        List<Map<String, Object>> items = ap.purchaseItems(poId);
        if (items.isEmpty()) return;
        Long whId = ap.purchaseWarehouse(poId);
        String poNo = ap.purchaseNo(poId);
        String date = LocalDate.now().toString();
        for (var it : items) {
            Long mid = ((Number) it.get("material_id")).longValue();
            BigDecimal qty = (BigDecimal) it.get("quantity");
            BigDecimal price = it.get("unit_price") == null ? BigDecimal.ZERO : (BigDecimal) it.get("unit_price");
            BigDecimal amt = it.get("amount") == null ? qty.multiply(price) : (BigDecimal) it.get("amount");
            BigDecimal before = ap.currentStock(mid);
            BigDecimal after = before.add(qty);
            ap.movementIn(ap.nextMovementId(), mid, whId, poId, qty, before, after, price, amt, date, "采购入库(审批)#" + poNo);
        }
    }

    /** 审批通过后：送货扣库存（含订单完成度重算） */
    private void applyDeliveryStock(Long dnId) {
        List<Map<String, Object>> items = ap.deliveryItems(dnId);
        if (items.isEmpty()) return;
        String dnNo = ap.deliveryNo(dnId);
        String date = LocalDate.now().toString();
        for (var it : items) {
            Long mid = ((Number) it.get("material_id")).longValue();
            BigDecimal qty = (BigDecimal) it.get("quantity");
            BigDecimal price = it.get("unit_price") == null ? BigDecimal.ZERO : (BigDecimal) it.get("unit_price");
            BigDecimal amt = it.get("amount") == null ? qty.multiply(price) : (BigDecimal) it.get("amount");
            BigDecimal before = ap.currentStock(mid);
            BigDecimal after = before.subtract(qty);
            ap.movementOut(ap.nextMovementId(), mid, null, dnId, qty, before, after, price, amt, date, "销售出货(审批)#" + dnNo);
        }
    }

    private String currentUser(String auth) {
        var u = sessionStore.verify(TokenUtils.extractToken(auth));
        return u == null ? "" : u.username();
    }
}
