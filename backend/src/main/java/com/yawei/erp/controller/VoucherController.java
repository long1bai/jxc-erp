package com.yawei.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import com.yawei.erp.common.ApiResponse;
import com.yawei.erp.common.PageResult;
import com.yawei.erp.util.SequenceUtil;
import com.yawei.erp.mapper.FinanceMapper;
import com.yawei.erp.mapper.SysMapper;
import com.yawei.erp.service.SysConfigService;

/** 收款单 / 付款单 */
@RestController
@RequestMapping("/api/vouchers")
public class VoucherController {

    private final FinanceMapper mapper;
    private final SysMapper sys;
    private final SequenceUtil seq;

        private final SysConfigService cfg;

public VoucherController(FinanceMapper mapper, SysMapper sys, SequenceUtil seq, SysConfigService cfg) {
        this.mapper = mapper;
        this.cfg = cfg;
        this.sys = sys;
        this.seq = seq;
    }

    // ============ 收款单 ============

    @GetMapping("/receipts")
    public Map<String, Object> receipts(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        return ApiResponse.ok(new PageResult(mapper.receiptList(keyword.trim(), p), p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @PostMapping("/receipts")
    @Transactional
    public Map<String, Object> createReceipt(@RequestBody ReceiptReq req) {
        if (req.customerId() == null || req.amount() == null || req.amount().signum() <= 0) {
            return ApiResponse.fail("请选择客户并填写收款金额");
        }
        String cname = sys.customerName(req.customerId());
        String rvNo = seq.nextDaily(cfg.get("seq_rcv"));
        mapper.receiptInsert(rvNo, req.customerId(), cname == null ? "" : cname, req.amount(),
                req.receiptDate() == null || req.receiptDate().isBlank() ? today() : req.receiptDate(),
                req.receiptMethod() == null ? "转账" : req.receiptMethod(), req.remark());
        return ApiResponse.ok(Map.of("id", mapper.lastReceiptId(rvNo), "rvNo", rvNo));
    }

    @DeleteMapping("/receipts/{id}")
    @Transactional
    public Map<String, Object> deleteReceipt(@PathVariable Long id) {
        if (mapper.receiptExists(id).isEmpty()) {
            return ApiResponse.fail("收款单不存在");
        }
        if (mapper.receiptSettled(id).signum() > 0) {
            return ApiResponse.fail("该收款单已核销，不能删除（可先撤销核销）");
        }
        mapper.receiptDelete(id);
        return ApiResponse.ok();
    }

    // ============ 付款单 ============

    @GetMapping("/payments")
    public Map<String, Object> payments(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        return ApiResponse.ok(new PageResult(mapper.paymentList(keyword.trim(), p), p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @PostMapping("/payments")
    @Transactional
    public Map<String, Object> createPayment(@RequestBody PaymentReq req) {
        if (req.supplierId() == null || req.amount() == null || req.amount().signum() <= 0) {
            return ApiResponse.fail("请选择供应商并填写付款金额");
        }
        String sname = sys.supplierName(req.supplierId());
        String pvNo = seq.nextDaily(cfg.get("seq_pay"));
        mapper.paymentInsert(pvNo, req.supplierId(), sname == null ? "" : sname, req.amount(),
                req.payDate() == null || req.payDate().isBlank() ? today() : req.payDate(),
                req.payMethod() == null ? "转账" : req.payMethod(), req.remark());
        return ApiResponse.ok(Map.of("id", mapper.lastPaymentId(pvNo), "pvNo", pvNo));
    }

    @DeleteMapping("/payments/{id}")
    @Transactional
    public Map<String, Object> deletePayment(@PathVariable Long id) {
        if (mapper.paymentExists(id).isEmpty()) {
            return ApiResponse.fail("付款单不存在");
        }
        if (mapper.paymentSettled(id).signum() > 0) {
            return ApiResponse.fail("该付款单已核销，不能删除（可先撤销核销）");
        }
        mapper.paymentDelete(id);
        return ApiResponse.ok();
    }

    private String today() {
        return java.time.LocalDate.now().toString();
    }

    public record ReceiptReq(Long customerId, BigDecimal amount, String receiptDate,
                             String receiptMethod, String remark) {}
    public record PaymentReq(Long supplierId, BigDecimal amount, String payDate,
                             String payMethod, String remark) {}
}
