package com.yawei.erp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 供应商管理 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierMapper mapper;

    public SupplierController(SupplierMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Supplier> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        java.util.List<Supplier> items = mapper.search(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Supplier s) {
        if (s.getName() == null || s.getName().isBlank()) {
            return ApiResponse.fail("供应商名称不能为空");
        }
        s.setName(s.getName().trim());
        long dup = mapper.selectCount(new LambdaQueryWrapper<Supplier>().eq(Supplier::getName, s.getName()));
        if (dup > 0) {
            return ApiResponse.fail("供应商名称已存在");
        }
        s.setId(null);
        mapper.insert(s);
        return ApiResponse.ok(s);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Supplier s) {
        if (mapper.selectById(id) == null) {
            return ApiResponse.fail("供应商不存在");
        }
        if (s.getName() == null || s.getName().isBlank()) {
            return ApiResponse.fail("供应商名称不能为空");
        }
        s.setName(s.getName().trim());
        var existing = mapper.selectOne(new LambdaQueryWrapper<Supplier>().eq(Supplier::getName, s.getName()));
        if (existing != null && !existing.getId().equals(id)) {
            return ApiResponse.fail("供应商名称已存在");
        }
        s.setId(id);
        mapper.updateById(s);
        return ApiResponse.ok(mapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.selectById(id) == null) {
            return ApiResponse.fail("供应商不存在");
        }
        Long refs = mapper.countRefs(id);
        if (refs != null && refs > 0) {
            return ApiResponse.fail("该供应商有采购/付款/退货记录，不能删除");
        }
        mapper.deleteById(id);
        return ApiResponse.ok();
    }
}
