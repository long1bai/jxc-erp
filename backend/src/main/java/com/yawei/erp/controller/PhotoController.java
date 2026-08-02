package com.yawei.erp.controller;

import com.yawei.erp.common.ApiResponse;
import com.yawei.erp.dto.PhotoDtos.ConfirmItem;
import com.yawei.erp.dto.PhotoDtos.ConfirmReq;
import com.yawei.erp.dto.PhotoDtos.PhotoReq;
import com.yawei.erp.service.PhotoService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 拍照入库（DashScope qwen3-vl-plus）。
 * 业务逻辑已抽取至 PhotoService（企业化分层 2026-08-02）。
 */
@RestController
@RequestMapping("/api/photo")
public class PhotoController {

    private final PhotoService service;

    public PhotoController(PhotoService service) {
        this.service = service;
    }

    // ============ 识别 ============

    @PostMapping("/recognize")
    public Map<String, Object> recognize(@RequestBody PhotoReq req) {
        if (req.imageBase64() == null || req.imageBase64().isBlank()) {
            return ApiResponse.fail("请上传图片");
        }
        try {
            return ApiResponse.ok(service.recognize(req.imageBase64()));
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.fail("图片识别失败：" + (e.getClass().getSimpleName()) + ": " + e.getMessage());
        }
    }

    // ============ 确认入库 ============

    /** 确认核对后的数据 → 生成采购入库单（自动补齐规格/自动新建物料 + 加库存流水） */
    @PostMapping("/confirm")
    @Transactional
    public Map<String, Object> confirm(@RequestBody ConfirmReq req) {
        if (req.supplierId() == null) {
            return ApiResponse.fail("请选择供应商");
        }
        if (req.items() == null || req.items().isEmpty()) {
            return ApiResponse.fail("请至少添加一条明细");
        }
        var result = service.confirm(req.supplierId(), req.poDate(), req.remark(),
                req.warehouseId(), req.items());
        if (result.containsKey("error")) {
            return ApiResponse.fail((String) result.get("error"));
        }
        return ApiResponse.ok(result);
    }
}
