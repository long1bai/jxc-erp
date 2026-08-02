package com.yawei.erp.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yawei.erp.dto.FinanceDtos.SettleReq;
import com.yawei.erp.dto.FinanceDtos.InvoiceReq;
import com.yawei.erp.mapper.FinanceMapper;
import com.yawei.erp.util.SequenceUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 财务总览业务层：应收/应付汇总、分月、单据列表（金蝶式）、核销、发票。
 * 逻辑自 FinanceController 抽取（2026-08-02 企业化分层重构），行为保持不变。
 */
@Service
public class FinanceService {

    private final FinanceMapper mapper;
    private final SequenceUtil seq;

        private final SysConfigService cfg;

public FinanceService(FinanceMapper mapper, SequenceUtil seq, SysConfigService cfg) {
        this.mapper = mapper;
        this.cfg = cfg;
        this.seq = seq;
    }

    // ============ 汇总 ============

    public List<Map<String, Object>> receivableSummary() {
        return mapper.receivableSummary();
    }

    public List<Map<String, Object>> payableSummary() {
        return mapper.payableSummary();
    }

    public List<Map<String, Object>> receivableMonthly() {
        return mapper.receivableMonthly();
    }

    public List<Map<String, Object>> payableMonthly() {
        return mapper.payableMonthly();
    }

    /** 账龄分析（应收/应付） */
    public List<Map<String, Object>> aging(String type) {
        return "purchase".equals(type) ? mapper.payableAging() : mapper.receivableAging();
    }

    // ============ 单据列表（金蝶式） ============

    public Map<String, Object> docList(String type, String keyword, String start, String end,
                                       String status, int page, int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = "purchase".equals(type)
                ? mapper.purchaseDocList(keyword.trim(), start, end, p)
                : mapper.deliveryDocList(keyword.trim(), start, end, p);
        // 结清状态：Java 层计算（未结清/部分结清/已结清）
        items.forEach(row -> {
            BigDecimal total = (BigDecimal) row.get("amount");
            BigDecimal settled = (BigDecimal) row.get("settled_amount");
            String st;
            if (settled.signum() <= 0) {
                st = "未结清";
            } else if (settled.compareTo(total) >= 0) {
                st = "已结清";
            } else {
                st = "部分结清";
            }
            row.put("settle_status", st);
        });
        if (status != null && !status.isBlank()) {
            String want = switch (status) {
                case "none" -> "未结清";
                case "partial" -> "部分结清";
                case "settled" -> "已结清";
                default -> null;
            };
            if (want != null) {
                items = items.stream().filter(r -> want.equals(r.get("settle_status"))).toList();
            }
        }
        return Map.of("items", items, "total", p.getTotal(), "page", p.getCurrent(), "size", p.getSize());
    }

    /** 单据核销记录 */
    public List<Map<String, Object>> settlements(String refType, Long refId) {
        return mapper.settleList(refType, refId);
    }

    /** 创建核销（收款单→送货单 / 付款单→采购单） */
    @Transactional
    public Map<String, Object> settle(SettleReq req) {
        // 方向校验：收款↔送货（应收），付款↔采购（应付）
        String settleType = "purchase".equals(req.refType()) ? "payment" : "receipt";
        // 单据未结余额
        BigDecimal docBal = mapper.docBalance(req.refType(), req.refId());
        if (docBal == null) {
            return Map.of("error", "关联单据不存在");
        }
        if (req.amount().compareTo(docBal) > 0) {
            return Map.of("error", "核销金额超过单据未结余额 " + docBal);
        }
        // 收/付款单未核销余额
        BigDecimal vBal = mapper.voucherBalance(settleType, req.voucherId());
        if (vBal == null) {
            return Map.of("error", "收/付款单不存在");
        }
        if (req.amount().compareTo(vBal) > 0) {
            return Map.of("error", "核销金额超过" + ("receipt".equals(settleType) ? "收款单" : "付款单") + "剩余 " + vBal);
        }
        var vinfo = mapper.voucherInfo(req.voucherId());
        String vno = vinfo.isEmpty() ? "" : String.valueOf(vinfo.get(0).get("vno"));
        Long id = mapper.nextId();
        mapper.settleInsert(id, settleType, req.voucherId(), vno, req.refType(), req.refId(),
                req.amount(), req.remark());
        return Map.of("id", id);
    }

    /** 撤销核销 */
    @Transactional
    public boolean revoke(Long id) {
        if (id == null || mapper.settleExists(id).isEmpty()) {
            return false;
        }
        mapper.settleDelete(id);
        return true;
    }

    // ============ 发票 ============

    public List<Map<String, Object>> invoices(String type, String keyword) {
        return mapper.invoiceList(type.isBlank() ? null : type, keyword.trim());
    }

    @Transactional
    public Map<String, Object> createInvoice(InvoiceReq req) {
        String refType = "purchase".equals(req.invoiceType()) ? "purchase" : "delivery";
        // 校验不超未开票余额
        BigDecimal balance = mapper.docBalance(refType, req.refId());
        if (balance == null) {
            return Map.of("error", "关联单据不存在");
        }
        // docBalance 是未结余额，未开票余额要单独算（发票不依赖核销）
        List<Map<String, Object>> refs = "purchase".equals(refType)
                ? mapper.purchaseInvoiceRefs(req.supplierId())
                : mapper.salesInvoiceRefs(req.customerId());
        BigDecimal uninv = refs.stream()
                .filter(r -> ((Number) r.get("id")).longValue() == req.refId())
                .map(r -> (BigDecimal) r.get("balance"))
                .findFirst().orElse(null);
        if (uninv == null) {
            return Map.of("error", "该单据未开票余额不足");
        }
        if (req.amount().compareTo(uninv) > 0) {
            return Map.of("error", "开票金额超过未开票余额 " + uninv);
        }
        String invoiceNo = seq.nextDaily(cfg.get("seq_inv"));
        mapper.invoiceInsert(invoiceNo, "purchase".equals(req.invoiceType()) ? "purchase" : "sales",
                refType, req.refId(), req.customerId(), req.customerName(),
                req.supplierId(), req.supplierName(), req.amount(),
                req.invoiceDate() == null || req.invoiceDate().isBlank() ? today() : req.invoiceDate(),
                req.remark());
        return Map.of("id", mapper.lastInvoiceId(invoiceNo), "invoiceNo", invoiceNo);
    }

    public boolean deleteInvoice(Long id) {
        if (mapper.invoiceExists(id).isEmpty()) {
            return false;
        }
        mapper.invoiceDelete(id);
        return true;
    }

    /** 待开票单据 */
    public List<Map<String, Object>> invoiceRefs(String type, Long partyId) {
        return "purchase".equals(type)
                ? mapper.purchaseInvoiceRefs(partyId)
                : mapper.salesInvoiceRefs(partyId);
    }

    private String today() {
        return java.time.LocalDate.now().toString();
    }
}
