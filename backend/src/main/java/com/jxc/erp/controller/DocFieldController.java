package com.jxc.erp.controller;

import com.jxc.erp.common.BaseController;
import com.jxc.erp.dto.DocFieldDtos.SaveReq;
import com.jxc.erp.service.DocFieldService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 单据动态字段管理（doc_field_defs）+ 双通道模板识别。
 * 通道一：POST /recognize/excel（POI 表头解析，默认）
 * 通道二：POST /recognize/ai（DashScope；inputMode=excel 文本 / photo 拍照）
 */
@RestController
@RequestMapping("/api/doc-fields")
public class DocFieldController extends BaseController {

    private final DocFieldService service;

    public DocFieldController(DocFieldService service) {
        this.service = service;
    }

    /** 某单据类型的启用字段定义 */
    @GetMapping
    public Map<String, Object> list(@RequestParam String docType) {
        return ok(service.list(docType));
    }

    /** 保存字段定义（按 doc_type 全量替换） */
    @PostMapping
    public Map<String, Object> save(@RequestBody SaveReq req) {
        if (req.docType() == null || req.docType().isBlank()) {
            return fail("缺少单据类型");
        }
        service.save(req.docType(), req.fields());
        return ok();
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        service.delete(id);
        return ok();
    }

    /** 通道一（默认）：POI 解析 Excel 表头 → 推断字段 */
    @PostMapping(value = "/recognize/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> recognizeExcel(@RequestParam("file") MultipartFile file,
                                              @RequestParam String docType) {
        if (file.isEmpty()) {
            return fail("请选择 Excel 文件");
        }
        try {
            return ok(service.recognizeExcel(file));
        } catch (IllegalArgumentException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("识别失败：" + e.getMessage());
        }
    }

    /** 通道二（AI）：Excel 文本（qwen-plus）/ 照片（qwen3-vl-plus） */
    @PostMapping(value = "/recognize/ai", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> recognizeAi(@RequestParam("file") MultipartFile file,
                                           @RequestParam String docType,
                                           @RequestParam(defaultValue = "excel") String inputMode) {
        if (file.isEmpty()) {
            return fail("请选择文件");
        }
        try {
            return ok(service.recognizeAi(file, inputMode));
        } catch (Exception e) {
            return fail("AI 识别失败：" + e.getMessage());
        }
    }
}
