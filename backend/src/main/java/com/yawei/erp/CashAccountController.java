package com.yawei.erp;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 资金账户管理 */
@RestController
@RequestMapping("/api/cash-accounts")
public class CashAccountController {

    private final AccountMapper mapper;

    public CashAccountController(AccountMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Map<String, Object> list() {
        return ApiResponse.ok(Map.of("items", mapper.accountList()));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody AccountReq req) {
        if (req.name() == null || req.name().isBlank()) {
            return ApiResponse.fail("请填写账户名称");
        }
        mapper.accountInsert(req.name().trim(), req.type() == null || req.type().isBlank() ? "cash" : req.type(),
                req.balance() == null ? BigDecimal.ZERO : req.balance(),
                req.remark() == null ? "" : req.remark());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody AccountReq req) {
        if (mapper.accountExists(id).isEmpty()) {
            return ApiResponse.fail("账户不存在");
        }
        if (req.name() == null || req.name().isBlank()) {
            return ApiResponse.fail("请填写账户名称");
        }
        mapper.accountUpdate(id, req.name().trim(), req.type() == null || req.type().isBlank() ? "cash" : req.type(),
                req.balance() == null ? BigDecimal.ZERO : req.balance(),
                req.remark() == null ? "" : req.remark());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.accountExists(id).isEmpty()) {
            return ApiResponse.fail("账户不存在");
        }
        // 账户有关联收支/转账时禁止删除（deleted 过滤会算错余额）
        List<Map<String, Object>> used = mapper.accountUsage(id);
        if (!used.isEmpty()) {
            return ApiResponse.fail("该账户已有收支或转账记录，不能删除（可修改名称）");
        }
        mapper.accountDelete(id);
        return ApiResponse.ok();
    }

    public record AccountReq(String name, String type, BigDecimal balance, String remark) {}
}
