package com.jxc.erp.controller;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.mapper.ExpressMapper;

/** 快递物流公司管理 */
@RestController
@RequestMapping("/api/express-companies")
public class ExpressController {

    private final ExpressMapper mapper;

    public ExpressController(ExpressMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Map<String, Object> list() {
        return ApiResponse.ok(Map.of("items", mapper.list()));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CompanyReq req) {
        if (req.name() == null || req.name().isBlank()) {
            return ApiResponse.fail("请填写物流公司名称");
        }
        mapper.insert(req.name().trim(), req.contact() == null ? "" : req.contact(),
                req.phone() == null ? "" : req.phone(), req.remark() == null ? "" : req.remark());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody CompanyReq req) {
        if (mapper.exists(id).isEmpty()) {
            return ApiResponse.fail("物流公司不存在");
        }
        mapper.update(id, req.name().trim(), req.contact() == null ? "" : req.contact(),
                req.phone() == null ? "" : req.phone(), req.remark() == null ? "" : req.remark());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.exists(id).isEmpty()) {
            return ApiResponse.fail("物流公司不存在");
        }
        mapper.delete(id);
        return ApiResponse.ok();
    }

    public record CompanyReq(String name, String contact, String phone, String remark) {}
}
