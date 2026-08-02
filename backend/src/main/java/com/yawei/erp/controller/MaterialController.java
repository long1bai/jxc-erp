package com.yawei.erp.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.yawei.erp.common.ApiResponse;
import com.yawei.erp.common.PageResult;
import com.yawei.erp.entity.Material;
import com.yawei.erp.mapper.MaterialMapper;

/** 物料/产品管理 */
@RestController
@RequestMapping("/api/materials")
public class MaterialController {

    private final MaterialMapper mapper;

    public MaterialController(MaterialMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Material> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        java.util.List<Material> items = mapper.search(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Material m) {
        if (m.getName() == null || m.getName().isBlank()) {
            return ApiResponse.fail("物料名称不能为空");
        }
        m.setName(m.getName().trim());
        if (m.getCode() != null && !m.getCode().isBlank()) {
            m.setCode(m.getCode().trim());
            long dup = mapper.selectCount(new LambdaQueryWrapper<Material>().eq(Material::getCode, m.getCode()));
            if (dup > 0) {
                return ApiResponse.fail("物料编号已存在");
            }
        }
        m.setId(null);
        mapper.insert(m);
        return ApiResponse.ok(m);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Material m) {
        if (mapper.selectById(id) == null) {
            return ApiResponse.fail("物料不存在");
        }
        if (m.getName() == null || m.getName().isBlank()) {
            return ApiResponse.fail("物料名称不能为空");
        }
        m.setName(m.getName().trim());
        if (m.getCode() != null && !m.getCode().isBlank()) {
            m.setCode(m.getCode().trim());
            var existing = mapper.selectOne(new LambdaQueryWrapper<Material>().eq(Material::getCode, m.getCode()));
            if (existing != null && !existing.getId().equals(id)) {
                return ApiResponse.fail("物料编号已存在");
            }
        }
        m.setId(id);
        mapper.updateById(m);
        return ApiResponse.ok(mapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.selectById(id) == null) {
            return ApiResponse.fail("物料不存在");
        }
        Long refs = mapper.countRefs(id);
        if (refs != null && refs > 0) {
            return ApiResponse.fail("该物料有订单/库存/BOM 记录，不能删除");
        }
        // 逻辑删除 + code 加后缀（软删后仍可重录同编码）
        mapper.softDeleteWithCodeSuffix(id);
        return ApiResponse.ok();
    }
}
