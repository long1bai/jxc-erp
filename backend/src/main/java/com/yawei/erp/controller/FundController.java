package com.yawei.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import com.yawei.erp.common.ApiResponse;
import com.yawei.erp.common.PageResult;
import com.yawei.erp.util.SequenceUtil;
import com.yawei.erp.mapper.AccountMapper;
import com.yawei.erp.service.SysConfigService;

/** 收支单 + 转账 + 资金报表（账户余额/收支统计/经营状况月报） */
@RestController
public class FundController {

    private final AccountMapper mapper;
    private final SequenceUtil seq;

        private final SysConfigService cfg;

public FundController(AccountMapper mapper, SequenceUtil seq, SysConfigService cfg) {
        this.mapper = mapper;
        this.cfg = cfg;
        this.seq = seq;
    }

    // ============ 收支单 ============

    @GetMapping("/api/income-expenses")
    public Map<String, Object> ieList(@RequestParam(defaultValue = "") String keyword,
                                      @RequestParam(defaultValue = "") String type,
                                      @RequestParam(defaultValue = "") String from,
                                      @RequestParam(defaultValue = "") String to,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.ieList(keyword.trim(), type, from, to, p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @PostMapping("/api/income-expenses")
    @Transactional
    public Map<String, Object> ieCreate(@RequestBody IeReq req) {
        if (req.amount() == null || req.amount().signum() <= 0) {
            return ApiResponse.fail("金额必须大于 0");
        }
        if (req.category() == null || req.category().isBlank()) {
            return ApiResponse.fail("请选择收支分类");
        }
        if (req.accountId() == null) {
            return ApiResponse.fail("请选择资金账户");
        }
        if (mapper.accountExists(req.accountId()).isEmpty()) {
            return ApiResponse.fail("资金账户不存在");
        }
        String type = "expense".equals(req.ieType()) ? "expense" : "income";
        String date = req.ieDate() == null || req.ieDate().isBlank() ? today() : req.ieDate();
        String ieNo = seq.nextDaily(cfg.get("seq_ie"));
        mapper.ieInsert(ieNo, type, req.category().trim(), req.accountId(),
                req.amount().setScale(2, java.math.RoundingMode.HALF_UP), date,
                req.remark() == null ? "" : req.remark());
        return ApiResponse.ok(Map.of("id", mapper.lastIeId(ieNo), "ieNo", ieNo));
    }

    @DeleteMapping("/api/income-expenses/{id}")
    @Transactional
    public Map<String, Object> ieDelete(@PathVariable Long id) {
        if (mapper.ieExists(id).isEmpty()) {
            return ApiResponse.fail("收支单不存在");
        }
        mapper.ieDelete(id);
        return ApiResponse.ok();
    }

    // ============ 转账 ============

    @GetMapping("/api/transfers")
    public Map<String, Object> tfList(@RequestParam(defaultValue = "") String keyword,
                                      @RequestParam(defaultValue = "") String from,
                                      @RequestParam(defaultValue = "") String to,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.tfList(keyword.trim(), from, to, p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @PostMapping("/api/transfers")
    @Transactional
    public Map<String, Object> tfCreate(@RequestBody TfReq req) {
        if (req.amount() == null || req.amount().signum() <= 0) {
            return ApiResponse.fail("转账金额必须大于 0");
        }
        if (req.fromId() == null || req.toId() == null || req.fromId().equals(req.toId())) {
            return ApiResponse.fail("转出/转入账户不能为空且不能相同");
        }
        if (mapper.accountExists(req.fromId()).isEmpty() || mapper.accountExists(req.toId()).isEmpty()) {
            return ApiResponse.fail("账户不存在");
        }
        String date = req.tfDate() == null || req.tfDate().isBlank() ? today() : req.tfDate();
        String tfNo = seq.nextDaily(cfg.get("seq_zz"));
        mapper.tfInsert(tfNo, req.fromId(), req.toId(),
                req.amount().setScale(2, java.math.RoundingMode.HALF_UP), date,
                req.remark() == null ? "" : req.remark());
        return ApiResponse.ok(Map.of("id", mapper.lastTfId(tfNo), "tfNo", tfNo));
    }

    @DeleteMapping("/api/transfers/{id}")
    @Transactional
    public Map<String, Object> tfDelete(@PathVariable Long id) {
        if (mapper.tfExists(id).isEmpty()) {
            return ApiResponse.fail("转账单不存在");
        }
        mapper.tfDelete(id);
        return ApiResponse.ok();
    }

    // ============ 资金报表 ============

    @GetMapping("/api/account-reports/balances")
    public Map<String, Object> balances() {
        return ApiResponse.ok(Map.of("items", mapper.accountBalances()));
    }

    @GetMapping("/api/account-reports/stats")
    public Map<String, Object> stats(@RequestParam(defaultValue = "") String type,
                                     @RequestParam(defaultValue = "") String from,
                                     @RequestParam(defaultValue = "") String to) {
        return ApiResponse.ok(Map.of("items", mapper.ieStats(type, from, to)));
    }

    @GetMapping("/api/account-reports/business")
    public Map<String, Object> business(@RequestParam(defaultValue = "") String from,
                                        @RequestParam(defaultValue = "") String to) {
        return ApiResponse.ok(Map.of("items", mapper.businessMonthly(from, to)));
    }

    private String today() {
        return LocalDate.now().toString();
    }

    public record IeReq(String ieType, String category, Long accountId, BigDecimal amount,
                        String ieDate, String remark) {}
    public record TfReq(Long fromId, Long toId, BigDecimal amount, String tfDate, String remark) {}
}
