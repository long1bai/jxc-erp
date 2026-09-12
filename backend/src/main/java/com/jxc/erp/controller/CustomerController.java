package com.jxc.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.common.PageResult;
import com.jxc.erp.entity.Customer;
import com.jxc.erp.mapper.CustomerMapper;

/** 客户管理 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerMapper mapper;

    public CustomerController(CustomerMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Customer> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        java.util.List<Customer> items = mapper.search(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Customer c) {
        if (c.getName() == null || c.getName().isBlank()) {
            return ApiResponse.fail("客户名称不能为空");
        }
        c.setName(c.getName().trim());
        long dup = mapper.selectCount(new LambdaQueryWrapper<Customer>().eq(Customer::getName, c.getName()));
        if (dup > 0) {
            return ApiResponse.fail("客户名称已存在");
        }
        c.setId(null);
        mapper.insert(c);
        return ApiResponse.ok(c);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Customer c) {
        if (mapper.selectById(id) == null) {
            return ApiResponse.fail("客户不存在");
        }
        if (c.getName() == null || c.getName().isBlank()) {
            return ApiResponse.fail("客户名称不能为空");
        }
        c.setName(c.getName().trim());
        var existing = mapper.selectOne(new LambdaQueryWrapper<Customer>().eq(Customer::getName, c.getName()));
        if (existing != null && !existing.getId().equals(id)) {
            return ApiResponse.fail("客户名称已存在");
        }
        c.setId(id);
        mapper.updateById(c);
        return ApiResponse.ok(mapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.selectById(id) == null) {
            return ApiResponse.fail("客户不存在");
        }
        Long refs = mapper.countRefs(id);
        if (refs != null && refs > 0) {
            return ApiResponse.fail("该客户有订单/送货/收款记录，不能删除");
        }
        mapper.deleteById(id);
        return ApiResponse.ok();
    }
}
