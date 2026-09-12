package com.jxc.erp.service;

import com.jxc.erp.mapper.ApprovalMapper;
import com.jxc.erp.mapper.SysMapper;
import com.jxc.erp.mapper.TradeMapper;
import com.jxc.erp.util.SequenceUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购单创建公共服务（2026-08-04 抽取，三处共用）：
 * PurchaseController.create / PhotoService.confirm / PurchaseImportService。
 * 行为差异由 checkApproval 区分：
 *  - true  = 走审批流（启用审批时建为 pending 不加库存）——原 PurchaseController.create 行为
 *  - false = 直接加库存流水（不走审批）——原 PhotoService.confirm 行为
 */
@Service
public class PurchaseCreationService {

    private final TradeMapper trade;
    private final SysMapper sys;
    private final SequenceUtil seq;
    private final ApprovalMapper approvalMapper;
    private final SysConfigService cfg;

    public PurchaseCreationService(TradeMapper trade, SysMapper sys, SequenceUtil seq,
                                   ApprovalMapper approvalMapper, SysConfigService cfg) {
        this.trade = trade;
        this.sys = sys;
        this.seq = seq;
        this.approvalMapper = approvalMapper;
        this.cfg = cfg;
    }

    /** 一条明细（导入时物料已匹配到 id） */
    public record Line(Long materialId, String materialName, String spec, String unit,
                       String assemblySystem, BigDecimal quantity, BigDecimal unitPrice) {}

    /** 采购单草稿（poNo 可空→自动生成序列号） */
    public record Draft(String poNo, Long supplierId, Long projectId, String projectName,
                        String poDate, String handler, String remark, Long warehouseId,
                        List<Line> items) {}

    /**
     * 创建采购单：主单 + 明细 + 审批钩子/库存流水。
     *
     * @return {id, poNo, pendingApproval, created}
     */
    @Transactional
    public Map<String, Object> create(Draft draft, boolean checkApproval) {
        String poNo = draft.poNo() == null || draft.poNo().isBlank()
                ? seq.nextDaily(cfg.get("seq_cgdd")) : draft.poNo().trim();
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (Line it : draft.items()) {
            totalQty = totalQty.add(nz(it.quantity()));
            totalAmt = totalAmt.add(amount(it.quantity(), it.unitPrice()));
        }
        String poDate = draft.poDate() == null || draft.poDate().isBlank() ? today() : draft.poDate();
        trade.purchaseInsert(poNo, draft.supplierId(), draft.projectId(), draft.projectName(),
                poDate, draft.handler(), totalQty, totalAmt, draft.remark());
        Long poId = trade.lastPurchaseId(poNo);

        int sort = 0;
        int validCount = 0;
        for (Line it : draft.items()) {
            if (it.materialId() == null || it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            BigDecimal amt = amount(it.quantity(), it.unitPrice());
            trade.purchaseItemInsert(poId, it.materialId(), str(it.materialName()), str(it.spec()), str(it.unit()),
                    str(it.assemblySystem()), it.quantity(), nz(it.unitPrice()), amt, sort++);
            validCount++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", poId);
        result.put("poNo", poNo);

        // 审批流：启用审批时创建为 pending 且不执行库存动作，审批通过后由 ApprovalController 加库存
        if (checkApproval && approvalMapper.approvalEnabled("purchase") == 1) {
            approvalMapper.updatePurchaseApprove(poId, "pending", "");
            result.put("pendingApproval", true);
            result.put("created", validCount);
            return result;
        }
        for (Line it : draft.items()) {
            if (it.materialId() == null || it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            BigDecimal before = currentStock(it.materialId());
            BigDecimal after = before.add(it.quantity());
            trade.movementInsertWh(it.materialId(), draft.warehouseId(), "in", "purchase", poId,
                    it.quantity(), before, after, nz(it.unitPrice()),
                    amount(it.quantity(), it.unitPrice()), today(), "采购入库#" + poNo);
        }
        result.put("pendingApproval", false);
        result.put("created", validCount);
        return result;
    }

    private BigDecimal currentStock(Long materialId) {
        BigDecimal v = sys.currentStock(materialId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal amount(BigDecimal qty, BigDecimal price) {
        if (qty == null || price == null) {
            return BigDecimal.ZERO;
        }
        return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private String str(String s) {
        return s == null ? "" : s;
    }

    private String today() {
        return java.time.LocalDate.now().toString();
    }
}
