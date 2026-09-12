package com.jxc.erp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.common.PageResult;
import com.jxc.erp.entity.Project;
import com.jxc.erp.mapper.ProjectMapper;

/** 项目（工地）管理 + 项目材料成本台账 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectMapper mapper;

    public ProjectController(ProjectMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.search(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        return ApiResponse.ok(Map.of("items", mapper.options()));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Project s) {
        if (s.getName() == null || s.getName().isBlank()) {
            return ApiResponse.fail("项目名称不能为空");
        }
        if (s.getCustomerId() == null) {
            return ApiResponse.fail("请选择客户");
        }
        s.setId(null);
        if (s.getStatus() == null || s.getStatus().isBlank()) s.setStatus("active");
        mapper.insert(s);
        return ApiResponse.ok(s);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Project s) {
        if (mapper.selectById(id) == null) {
            return ApiResponse.fail("项目不存在");
        }
        if (s.getName() == null || s.getName().isBlank()) {
            return ApiResponse.fail("项目名称不能为空");
        }
        s.setId(id);
        mapper.updateById(s);
        return ApiResponse.ok(mapper.selectById(id));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        if (mapper.selectById(id) == null) {
            return ApiResponse.fail("项目不存在");
        }
        Long refs = mapper.countPurchases(id);
        if (refs != null && refs > 0) {
            return ApiResponse.fail("该项目已有采购入库记录，不能删除");
        }
        mapper.deleteById(id);
        return ApiResponse.ok();
    }

    /** 项目材料成本台账：{project, items, summary, totals} */
    @GetMapping("/{id}/cost")
    public Map<String, Object> cost(@PathVariable Long id) {
        List<Map<String, Object>> proj = mapper.costProject(id);
        if (proj.isEmpty()) {
            return ApiResponse.fail("项目不存在");
        }
        return ApiResponse.ok(Map.of(
                "project", proj.get(0),
                "items", mapper.costItems(id),
                "summary", mapper.costSummary(id),
                "totals", mapper.costTotals(id)));
    }
}
