package com.yawei.erp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 仓库管理（示范：新 Controller 继承 BaseController，分页/响应统一） */
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController extends BaseController {

    private final WarehouseMapper mapper;

    public WarehouseController(WarehouseMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Warehouse> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        var qw = new LambdaQueryWrapper<Warehouse>()
                .like(!keyword.isBlank(), Warehouse::getName, keyword.trim())
                .orderByAsc(Warehouse::getId);
        java.util.List<Warehouse> items = mapper.selectList(qw);
        // 手动分页（仓库数量少，直接全量返回更简单）
        int total = items.size();
        int from = (int) Math.min((long) (Math.max(page, 1) - 1) * size, items.size());
        int to = (int) Math.min((long) from + size, items.size());
        return ok(new PageResult(items.subList(from, to), total, Math.max(page, 1), size).toMap());
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Warehouse w) {
        if (w.getName() == null || w.getName().isBlank()) {
            return fail("仓库名称不能为空");
        }
        w.setName(w.getName().trim());
        long dup = mapper.selectCount(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getName, w.getName()));
        if (dup > 0) {
            return fail("仓库名称已存在");
        }
        w.setId(null);
        mapper.insert(w);
        return ok(w);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Warehouse w) {
        if (mapper.selectById(id) == null) {
            return fail("仓库不存在");
        }
        if (w.getName() == null || w.getName().isBlank()) {
            return fail("仓库名称不能为空");
        }
        w.setName(w.getName().trim());
        var existing = mapper.selectOne(new LambdaQueryWrapper<Warehouse>().eq(Warehouse::getName, w.getName()));
        if (existing != null && !existing.getId().equals(id)) {
            return fail("仓库名称已存在");
        }
        w.setId(id);
        mapper.updateById(w);
        return ok(mapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.selectById(id) == null) {
            return fail("仓库不存在");
        }
        if (mapper.countMovements(id) > 0) {
            return fail("该仓库已有库存流水，不能删除（可改名称或停用）");
        }
        mapper.deleteById(id);
        return ok();
    }
}
