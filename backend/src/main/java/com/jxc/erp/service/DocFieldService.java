package com.jxc.erp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jxc.erp.client.DashScopeClient;
import com.jxc.erp.dto.DocFieldDtos.FieldDef;
import com.jxc.erp.dto.DocFieldDtos.RecognizeResult;
import com.jxc.erp.mapper.DocFieldMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单据动态字段：元数据 CRUD + 双通道模板识别。
 * 通道一（默认）：POI 解析 Excel 表头 + 示例行，规则推断字段类型。
 * 通道二（AI）：复用 DashScopeClient（Excel 走 qwen-plus 文本 / 照片走 qwen3-vl-plus 多模态）。
 * 字段定义只描述"展示"，不参与统计/筛选（docs/08-dev-notes.md 纪律）。
 */
@Service
public class DocFieldService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DocFieldMapper mapper;
    private final DashScopeClient dash;

    @Value("${app.ai-config}")
    private String aiConfigPath;

    @Value("${app.photo-config}")
    private String photoConfigPath;

    public DocFieldService(DocFieldMapper mapper, DashScopeClient dash) {
        this.mapper = mapper;
        this.dash = dash;
    }

    // ============ 字段定义 CRUD ============

    public List<Map<String, Object>> list(String docType) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : mapper.listFields(docType)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("fieldKey", r.get("field_key"));
            m.put("fieldName", r.get("field_name"));
            m.put("fieldType", r.get("field_type"));
            m.put("options", parseOptions(r.get("options")));
            m.put("sortOrder", r.get("sort_order"));
            out.add(m);
        }
        return out;
    }

    /** 按 doc_type 全量替换（物理删旧定义 → 逐条 upsert，未传入的直接消失） */
    @Transactional
    public void save(String docType, List<FieldDef> fields) {
        mapper.deleteByDocType(docType);
        int sort = 0;
        for (FieldDef f : fields == null ? List.<FieldDef>of() : fields) {
            String key = trim(f.fieldKey());
            String name = trim(f.fieldName());
            if (key.isBlank() || name.isBlank()) {
                continue;
            }
            String type = normalizeType(f.fieldType());
            String options = serializeOptions(f.options());
            Long id = mapper.findIdByKey(docType, key);
            if (id != null) {
                mapper.updateField(id, name, type, options, sort);
            } else {
                mapper.insertField(docType, key, name, type, options, sort);
            }
            sort++;
        }
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    // ============ 通道一：传统解析（默认） ============

    public RecognizeResult recognizeExcel(MultipartFile file) throws Exception {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 没有工作表");
            }
            // 表头 = 首个非空行
            Row header = null;
            int headerIdx = sheet.getFirstRowNum();
            for (int i = sheet.getFirstRowNum(); i <= Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 5); i++) {
                Row r = sheet.getRow(i);
                if (r != null && hasText(r)) {
                    header = r;
                    headerIdx = i;
                    break;
                }
            }
            if (header == null) {
                throw new IllegalArgumentException("Excel 为空，无法识别表头");
            }
            int cols = Math.max(header.getLastCellNum(), 1);
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < cols; c++) {
                headers.add(cellStr(header.getCell(c)).trim());
            }

            // 收集示例行（表头下最多 20 行非空）+ 每列样本值
            List<List<String>> sampleRows = new ArrayList<>();
            Map<Integer, List<String>> colSamples = new HashMap<>();
            for (int i = headerIdx + 1; i <= sheet.getLastRowNum() && sampleRows.size() < 20; i++) {
                Row r = sheet.getRow(i);
                if (r == null) {
                    continue;
                }
                boolean any = false;
                List<String> rowVals = new ArrayList<>();
                for (int c = 0; c < cols; c++) {
                    String v = cellStr(r.getCell(c)).trim();
                    rowVals.add(v);
                    if (!v.isEmpty()) {
                        any = true;
                        colSamples.computeIfAbsent(c, k -> new ArrayList<>()).add(v);
                    }
                }
                if (any) {
                    sampleRows.add(rowVals);
                }
            }

            // 逐列推断类型（跳过标准列）
            List<FieldDef> fields = new ArrayList<>();
            for (int c = 0; c < cols; c++) {
                String name = headers.get(c);
                if (isStandardColumn(name)) {
                    continue;
                }
                String type = inferType(colSamples.get(c));
                List<String> options = "select".equals(type) ? distinct(colSamples.get(c)) : List.of();
                fields.add(new FieldDef("f" + (c + 1), name, type, options, c));
            }
            return new RecognizeResult("excel", fields, headers, sampleRows);
        }
    }

    // ============ 通道二：AI 识别 ============

    public RecognizeResult recognizeAi(MultipartFile file, String inputMode) throws Exception {
        String reply;
        if ("photo".equalsIgnoreCase(inputMode)) {
            // 多模态：base64 → data URL，复用 PhotoService 同款结构
            byte[] bytes = file.getBytes();
            String mime = file.getContentType();
            String type = (mime != null && mime.startsWith("image/")) ? mime : "image/jpeg";
            String dataUrl = "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
            Object content = List.of(
                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                    Map.of("type", "text", "text", AI_PROMPT));
            reply = dash.chatForcedPrompt(photoConfigPath, "qwen3-vl-plus", AI_PROMPT, content);
        } else {
            // Excel 文本模式：表头 + 前 5 行示例拼成文本
            String textBlock;
            try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
                textBlock = buildExcelText(wb.getSheetAt(0));
            }
            reply = dash.chatForcedPrompt(aiConfigPath, "qwen-plus", AI_PROMPT, textBlock);
        }

        Map<String, Object> parsed = parseJson(reply);
        List<FieldDef> fields = new ArrayList<>();
        if (parsed.get("fields") instanceof List<?> fArr) {
            int sort = 0;
            for (Object o : fArr) {
                if (!(o instanceof Map<?, ?> fm)) {
                    continue;
                }
                String key = trim(String.valueOf(fm.get("field_key")));
                String name = trim(String.valueOf(fm.get("field_name")));
                String type = normalizeType(String.valueOf(fm.get("field_type")));
                List<String> options = optionsOf(fm.get("options"));
                if (key.isBlank() || name.isBlank()) {
                    continue;
                }
                fields.add(new FieldDef(key, name, type, options, sort++));
            }
        }
        // 照片模式无表头/示例行
        List<String> sampleHeaders = parsed.get("sample_headers") instanceof List<?> sh
                ? sh.stream().map(String::valueOf).toList() : List.of();
        return new RecognizeResult("ai", fields, sampleHeaders, List.of());
    }

    // ============ 工具 ============

    /** 标准列（已有正式列/业务字段，不作为动态字段） */
    private static final Set<String> STANDARD_COLS = Set.of(
            "采购单号", "单号", "日期", "时间", "供应商", "供应商名称", "物料名称", "物料", "名称",
            "规格", "规格型号", "单位", "数量", "单价", "金额", "经手人", "备注", "装配系统", "项目", "项目名称");

    /** 标准列判定：去空格后精确匹配（不能用 contains——"日期"会误杀"检查日期"等自定义列） */
    private boolean isStandardColumn(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        return STANDARD_COLS.contains(name.replaceAll("\\s", ""));
    }

    /** 用示例值推断列类型：全日期→date / 全数值→number / distinct≤10 且有重复→select / 否则 text */
    private String inferType(List<String> samples) {
        if (samples == null || samples.isEmpty()) {
            return "text";
        }
        boolean allDate = true, allNumber = true;
        for (String v : samples) {
            if (!isDate(v)) {
                allDate = false;
            }
            if (!isNumber(v)) {
                allNumber = false;
            }
        }
        if (allDate) {
            return "date";
        }
        if (allNumber) {
            return "number";
        }
        List<String> d = distinct(samples);
        if (d.size() <= 10 && d.size() < samples.size()) {
            return "select";
        }
        return "text";
    }

    private boolean isDate(String v) {
        return v != null && v.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}");
    }

    private boolean isNumber(String v) {
        try {
            new BigDecimal(v);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    /** 读 Excel 表头 + 前 5 行，拼成给模型的文本块 */
    private String buildExcelText(Sheet sheet) {
        StringBuilder sb = new StringBuilder("以下是一张采购单 Excel 的表头和示例数据：\n\n");
        int max = Math.min(sheet.getLastRowNum() + 1, sheet.getFirstRowNum() + 6);
        for (int i = sheet.getFirstRowNum(); i < max; i++) {
            Row r = sheet.getRow(i);
            if (r == null) {
                continue;
            }
            List<String> vals = new ArrayList<>();
            for (int c = 0; c < r.getLastCellNum(); c++) {
                vals.add(cellStr(r.getCell(c)).trim());
            }
            sb.append("| ").append(String.join(" | ", vals)).append(" |\n");
        }
        return sb.toString();
    }

    private boolean hasText(Row r) {
        for (int c = 0; c < r.getLastCellNum(); c++) {
            if (!cellStr(r.getCell(c)).trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String cellStr(Cell cell) {
        if (cell == null) {
            return "";
        }
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                        : trimNumber(cell.getNumericCellValue());
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> String.valueOf(cell.getNumericCellValue());
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private String trimNumber(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    // ============ JSON / 类型工具 ============

    private String normalizeType(String t) {
        if (t == null) {
            return "text";
        }
        String v = t.trim().toLowerCase();
        if (v.contains("date") || v.contains("日期")) {
            return "date";
        }
        if (v.contains("number") || v.contains("数字") || v.contains("数量")
                || v.contains("单价") || v.contains("金额")) {
            return "number";
        }
        if (v.contains("select") || v.contains("下拉") || v.contains("枚举")) {
            return "select";
        }
        return "text";
    }

    @SuppressWarnings("unchecked")
    private List<String> optionsOf(Object o) {
        if (o instanceof List<?> l) {
            return l.stream().map(String::valueOf).toList();
        }
        if (o instanceof String s && !s.isBlank()) {
            try {
                return JSON.readValue(s, List.class);
            } catch (Exception e) {
                // 逗号分隔兜底
                return java.util.Arrays.stream(s.split("[，,]")).map(String::trim)
                        .filter(x -> !x.isEmpty()).toList();
            }
        }
        return List.of();
    }

    private String serializeOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(options);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseOptions(Object o) {
        if (o == null) {
            return List.of();
        }
        try {
            return JSON.readValue(String.valueOf(o), List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String text) throws Exception {
        String t = text == null ? "" : text.trim();
        t = t.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        int s = t.indexOf('{');
        int e = t.lastIndexOf('}');
        if (s >= 0 && e > s) {
            t = t.substring(s, e + 1);
        }
        return JSON.readValue(t, Map.class);
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static final String AI_PROMPT = """
            你是一个单据模板识别专家。请从下面的采购单表头/示例数据（或单据照片）中，识别出【除标准列之外】的自定义列，作为该单据的动态字段定义。
            标准列（一律不要识别）：采购单号、日期、供应商、物料名称、物料、规格、规格型号、单位、数量、单价、金额、经手人、备注、装配系统、项目。
            只返回 JSON（不要其他文字）：
            {
              "fields": [
                {"field_key":"usage","field_name":"用途","field_type":"text","options":[]},
                {"field_key":"device_no","field_name":"设备编号","field_type":"text","options":[]}
              ],
              "sample_headers": [],
              "sample_rows": []
            }
            要求：
            1. field_type 只允许 text / number / date / select 之一；select 类型必须从示例数据去重填 options
            2. field_key 用 小写字母数字下划线（如 usage、device_no），不要中文
            3. field_name 用中文显示名
            4. 只输出自定义列，标准列一律不输出；没有自定义列返回 "fields": []
            5. 识别不清的列不要编造""";
}
