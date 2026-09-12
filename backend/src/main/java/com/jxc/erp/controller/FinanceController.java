package com.jxc.erp.controller;

import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.dto.FinanceDtos.SettleReq;
import com.jxc.erp.dto.FinanceDtos.InvoiceReq;
import com.jxc.erp.service.FinanceService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 财务总览：应收/应付汇总、分月、单据列表（金蝶式）、核销、发票。
 * 业务逻辑已抽取至 FinanceService（企业化分层 2026-08-02）。
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceService service;

    public FinanceController(FinanceService service) {
        this.service = service;
    }

    // ============ 汇总 ============

    @GetMapping("/receivable-summary")
    public Map<String, Object> receivableSummary() {
        return ApiResponse.ok(Map.of("items", service.receivableSummary()));
    }

    @GetMapping("/payable-summary")
    public Map<String, Object> payableSummary() {
        return ApiResponse.ok(Map.of("items", service.payableSummary()));
    }

    @GetMapping("/receivable-monthly")
    public Map<String, Object> receivableMonthly() {
        return ApiResponse.ok(Map.of("items", service.receivableMonthly()));
    }

    @GetMapping("/payable-monthly")
    public Map<String, Object> payableMonthly() {
        return ApiResponse.ok(Map.of("items", service.payableMonthly()));
    }

    /** 账龄分析（应收/应付） */
    @GetMapping("/aging")
    public Map<String, Object> aging(@RequestParam(defaultValue = "sales") String type) {
        return ApiResponse.ok(Map.of("items", service.aging(type)));
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
        return ApiResponse.ok(service.docList(type, keyword, start, end, status, page, size));
    }

    /** 单据核销记录 */
    @GetMapping("/settlements")
    public Map<String, Object> settlements(@RequestParam String refType, @RequestParam Long refId) {
        return ApiResponse.ok(Map.of("items", service.settlements(refType, refId)));
    }

    /** 创建核销（收款单→送货单 / 付款单→采购单） */
    @PostMapping("/settle")
    @Transactional
    public Map<String, Object> settle(@RequestBody SettleReq req) {
        if (req.voucherId() == null || req.refId() == null || req.amount() == null || req.amount().signum() <= 0) {
            return ApiResponse.fail("参数不完整");
        }
        var result = service.settle(req);
        if (result.containsKey("error")) {
            return ApiResponse.fail((String) result.get("error"));
        }
        return ApiResponse.ok(result);
    }

    /** 撤销核销 */
    @PostMapping("/settle/revoke")
    @Transactional
    public Map<String, Object> revoke(@RequestBody Map<String, Long> body) {
        if (!service.revoke(body.get("id"))) {
            return ApiResponse.fail("核销记录不存在");
        }
        return ApiResponse.ok();
    }

    // ============ 发票 ============

    @GetMapping("/invoices")
    public Map<String, Object> invoices(
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.ok(Map.of("items", service.invoices(type, keyword)));
    }

    @PostMapping("/invoices")
    @Transactional
    public Map<String, Object> createInvoice(@RequestBody InvoiceReq req) {
        if (req.refId() == null || req.amount() == null || req.amount().signum() <= 0) {
            return ApiResponse.fail("请选择单据并填写开票金额");
        }
        var result = service.createInvoice(req);
        if (result.containsKey("error")) {
            return ApiResponse.fail((String) result.get("error"));
        }
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/invoices/{id}")
    public Map<String, Object> deleteInvoice(@PathVariable Long id) {
        if (!service.deleteInvoice(id)) {
            return ApiResponse.fail("发票记录不存在");
        }
        return ApiResponse.ok();
    }

    /** 待开票单据 */
    @GetMapping("/invoice-refs")
    public Map<String, Object> invoiceRefs(
            @RequestParam(defaultValue = "sales") String type,
            @RequestParam(required = false) Long partyId) {
        return ApiResponse.ok(Map.of("items", service.invoiceRefs(type, partyId)));
    }
}
