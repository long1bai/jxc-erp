package com.jxc.erp.controller;

import com.jxc.erp.common.BaseController;
import com.jxc.erp.dto.DocFieldDtos.ImportResult;
import com.jxc.erp.service.PurchaseImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

/** 采购单文档能力：导入 / 导出 / 导入模板（单据能力中心一期） */
@RestController
@RequestMapping("/api/purchases")
public class PurchaseDocController extends BaseController {

    private final PurchaseImportService service;

    public PurchaseDocController(PurchaseImportService service) {
        this.service = service;
    }

    /** 明细级平铺导入：有错行时返回 fail + errorRows 明细（前端 e.detail） */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return fail("请选择 Excel 文件");
        }
        try {
            ImportResult result = service.importExcel(file);
            if (!result.errorRows().isEmpty()) {
                Map<String, Object> detail = Map.of(
                        "errorRows", result.errorRows(),
                        "okCount", result.okCount(),
                        "created", result.created(),
                        "skipped", result.skipped());
                return fail("导入完成，但 " + result.errorRows().size() + " 行失败，已导入成功行", detail);
            }
            return ok(result);
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("导入失败：" + e.getMessage());
        }
    }

    /** 导出采购单列表（含动态字段列） */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "") String keyword) throws IOException {
        byte[] bytes = service.buildExport(keyword.trim());
        return download(bytes, "采购单列表_" + LocalDate.now() + ".xlsx");
    }

    /** 下载导入模板（含 select 动态列下拉） */
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate() throws IOException {
        byte[] bytes = service.buildTemplate();
        return download(bytes, "采购单导入模板.xlsx");
    }

    private ResponseEntity<byte[]> download(byte[] bytes, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}
