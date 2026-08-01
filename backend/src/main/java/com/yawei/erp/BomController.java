package com.yawei.erp;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** BOM 管理：成品的组件配方维护（保存=先清后插） */
@RestController
@RequestMapping("/api/bom")
public class BomController {

    private final BomMapper mapper;

    public BomController(BomMapper mapper) {
        this.mapper = mapper;
    }

    /** 有 BOM 的成品列表 */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Map<String, Object>> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100));
        List<Map<String, Object>> items = mapper.bomProducts(keyword.trim(), p);
        return ApiResponse.ok(new PageResult(items, p.getTotal(), p.getCurrent(), p.getSize()).toMap());
    }

    /** 成品组件明细 */
    @GetMapping("/{productId}")
    public Map<String, Object> detail(@PathVariable Long productId) {
        return ApiResponse.ok(Map.of("items", mapper.bomItems(productId)));
    }

    /** 保存 BOM（先清后插，幂等） */
    @PostMapping("/{productId}")
    @Transactional
    public Map<String, Object> save(@PathVariable Long productId, @RequestBody BomSaveReq req) {
        if (req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请至少添加一个组件");
        }
        mapper.bomDeleteByProduct(productId);
        for (BomItemReq it : req.items()) {
            if (it.componentId() == null || it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            mapper.bomInsert(productId, it.componentId(), it.quantity());
        }
        return ApiResponse.ok(Map.of("id", productId, "count", mapper.bomItems(productId).size()));
    }

    /** 清空某成品的 BOM */
    @DeleteMapping("/{productId}")
    public Map<String, Object> clear(@PathVariable Long productId) {
        mapper.bomDeleteByProduct(productId);
        return ApiResponse.ok();
    }

    public record BomSaveReq(List<BomItemReq> items) {}
    public record BomItemReq(Long componentId, BigDecimal quantity) {}
}
