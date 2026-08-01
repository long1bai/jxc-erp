package com.yawei.erp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 财务总览：应收/应付汇总、分月、单据列表（金蝶式）、核销、发票 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceMapper mapper;
    private final SequenceUtil seq;

    public FinanceController(FinanceMapper mapper, SequenceUtil seq) {
        this.mapper = mapper;
        this.seq = seq;
    }

    // ============ 汇总 ============

    @GetMapping("/receivable-summary")
    public Map<String, Object> receivableSummary() {
        return ApiResponse.ok(Map.of("items", mapper.receivableSummary()));
    }

    @GetMapping("/payable-summary")
    public Map<String, Object> payableSummary() {
        return ApiResponse.ok(Map.of("items", mapper.payableSummary()));
    }

    @GetMapping("/receivable-monthly")
    public Map<String, Object> receivableMonthly() {
        return ApiResponse.ok(Map.of("items", mapper.receivableMonthly()));
    }

    @GetMapping("/payable-monthly")
    public Map<String, Object> payableMonthly() {
        return ApiResponse.ok(Map.of("items", mapper.payableMonthly()));
    }

    /** 账龄分析（应收/应付） */
    @GetMapping("/aging")
    public Map<String, Object> aging(@RequestParam(defaultValue = "sales") String type) {
        return ApiResponse.ok(Map.of("items", "purchase".equals(type)
                ? mapper.payableAging() : mapper.receivableAging()));
    }

    // ============ 单据列表（金蝶式） ============

    @GetMapping("/doc-list")
    public Map<String, Object> docList(
            @RequestParam(defaultValue = "sales") String type,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "") String start,
            @RequestParam(defaultValue = "") String end,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
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
        return ApiResponse.ok(Map.of("items", items, "total", p.getTotal(), "page", p.getCurrent(), "size", p.getSize()));
    }

    /** 单据核销记录 */
    @GetMapping("/settlements")
    public Map<String, Object> settlements(@RequestParam String refType, @RequestParam Long refId) {
        return ApiResponse.ok(Map.of("items", mapper.settleList(refType, refId)));
    }

    /** 创建核销（收款单→送货单 / 付款单→采购单） */
    @PostMapping("/settle")
    @Transactional
    public Map<String, Object> settle(@RequestBody SettleReq req) {
        // 方向校验：收款↔送货（应收），付款↔采购（应付）
        String settleType = "purchase".equals(req.refType()) ? "payment" : "receipt";
        if (req.voucherId() == null || req.refId() == null || req.amount() == null || req.amount().signum() <= 0) {
            return ApiResponse.fail("参数不完整");
        }
        // 单据未结余额
        BigDecimal docBal = mapper.docBalance(req.refType(), req.refId());
        if (docBal == null) {
            return ApiResponse.fail("关联单据不存在");
        }
        if (req.amount().compareTo(docBal) > 0) {
            return ApiResponse.fail("核销金额超过单据未结余额 " + docBal);
        }
        // 收/付款单未核销余额
        BigDecimal vBal = mapper.voucherBalance(settleType, req.voucherId());
        if (vBal == null) {
            return ApiResponse.fail("收/付款单不存在");
        }
        if (req.amount().compareTo(vBal) > 0) {
            return ApiResponse.fail("核销金额超过" + ("receipt".equals(settleType) ? "收款单" : "付款单") + "剩余 " + vBal);
        }
        var vinfo = mapper.voucherInfo(req.voucherId());
        String vno = vinfo.isEmpty() ? "" : String.valueOf(vinfo.get(0).get("vno"));
        Long id = mapper.nextId();
        mapper.settleInsert(id, settleType, req.voucherId(), vno, req.refType(), req.refId(),
                req.amount(), req.remark());
        return ApiResponse.ok(Map.of("id", id));
    }

    /** 撤销核销 */
    @PostMapping("/settle/revoke")
    @Transactional
    public Map<String, Object> revoke(@RequestBody Map<String, Long> body) {
        Long id = body.get("id");
        if (id == null || mapper.settleExists(id).isEmpty()) {
            return ApiResponse.fail("核销记录不存在");
        }
        mapper.settleDelete(id);
        return ApiResponse.ok();
    }

    // ============ 发票 ============

    @GetMapping("/invoices")
    public Map<String, Object> invoices(
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(Map.of("items", mapper.invoiceList(
                type.isBlank() ? null : type, keyword.trim())));
    }

    @PostMapping("/invoices")
    @Transactional
    public Map<String, Object> createInvoice(@RequestBody InvoiceReq req) {
        if (req.refId() == null || req.amount() == null || req.amount().signum() <= 0) {
            return ApiResponse.fail("请选择单据并填写开票金额");
        }
        String refType = "purchase".equals(req.invoiceType()) ? "purchase" : "delivery";
        // 校验不超未开票余额
        BigDecimal balance = mapper.docBalance(refType, req.refId());
        if (balance == null) {
            return ApiResponse.fail("关联单据不存在");
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
            return ApiResponse.fail("该单据未开票余额不足");
        }
        if (req.amount().compareTo(uninv) > 0) {
            return ApiResponse.fail("开票金额超过未开票余额 " + uninv);
        }
        String invoiceNo = seq.nextDaily("INV");
        mapper.invoiceInsert(invoiceNo, "purchase".equals(req.invoiceType()) ? "purchase" : "sales",
                refType, req.refId(), req.customerId(), req.customerName(),
                req.supplierId(), req.supplierName(), req.amount(),
                req.invoiceDate() == null || req.invoiceDate().isBlank() ? today() : req.invoiceDate(),
                req.remark());
        return ApiResponse.ok(Map.of("id", mapper.lastInvoiceId(invoiceNo), "invoiceNo", invoiceNo));
    }

    @DeleteMapping("/invoices/{id}")
    public Map<String, Object> deleteInvoice(@PathVariable Long id) {
        if (mapper.invoiceExists(id).isEmpty()) {
            return ApiResponse.fail("发票记录不存在");
        }
        mapper.invoiceDelete(id);
        return ApiResponse.ok();
    }

    /** 待开票单据 */
    @GetMapping("/invoice-refs")
    public Map<String, Object> invoiceRefs(
            @RequestParam(defaultValue = "sales") String type,
            @RequestParam(required = false) Long partyId) {
        List<Map<String, Object>> items = "purchase".equals(type)
                ? mapper.purchaseInvoiceRefs(partyId)
                : mapper.salesInvoiceRefs(partyId);
        return ApiResponse.ok(Map.of("items", items));
    }

    private String today() {
        return java.time.LocalDate.now().toString();
    }

    public record SettleReq(String refType, Long refId, Long voucherId, BigDecimal amount, String remark) {}
    public record InvoiceReq(String invoiceType, Long refId, Long customerId, String customerName,
                             Long supplierId, String supplierName, BigDecimal amount,
                             String invoiceDate, String remark) {}
}
