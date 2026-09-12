package com.jxc.erp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jxc.erp.dto.DocFieldDtos.ImportError;
import com.jxc.erp.dto.DocFieldDtos.ImportResult;
import com.jxc.erp.entity.Material;
import com.jxc.erp.entity.Project;
import com.jxc.erp.entity.Supplier;
import com.jxc.erp.mapper.DocFieldMapper;
import com.jxc.erp.mapper.MaterialMapper;
import com.jxc.erp.mapper.ProjectMapper;
import com.jxc.erp.mapper.SupplierMapper;
import com.jxc.erp.mapper.TradeMapper;
import com.jxc.erp.util.ExcelUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 采购单导入/导出/模板（单据能力中心）。
 * 导入：明细级平铺（一行一条明细，同一"采购单号"行归组为一单；空单号每行自成单）。
 * 幂等：库中已存在 po_no 跳过（po_no UNIQUE），重传同一文件天然幂等。
 * 建单走 PurchaseCreationService（checkApproval=true，导入不绕过审批）。
 */
@Service
public class PurchaseImportService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DOC_TYPE = "purchase_order";
    private static final int MAX_IMPORT_ROWS = 2000;

    private static final List<String> TEMPLATE_COLS = List.of(
            "采购单号", "日期", "供应商名称", "物料名称", "规格", "单位", "数量", "单价", "经手人", "项目名称", "备注");
    private static final List<String> EXPORT_COLS = List.of(
            "采购单号", "日期", "供应商名称", "经手人", "项目名称", "总数量", "总金额", "备注");

    private final TradeMapper trade;
    private final DocFieldMapper docFieldMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final ProjectMapper projectMapper;
    private final PurchaseCreationService creationService;
    private final SysConfigService cfg;

    public PurchaseImportService(TradeMapper trade, DocFieldMapper docFieldMapper,
                                 SupplierMapper supplierMapper, MaterialMapper materialMapper,
                                 ProjectMapper projectMapper, PurchaseCreationService creationService,
                                 SysConfigService cfg) {
        this.trade = trade;
        this.docFieldMapper = docFieldMapper;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.projectMapper = projectMapper;
        this.creationService = creationService;
        this.cfg = cfg;
    }

    // ============ 导入 ============

    /** 一条校验通过的有效明细行 */
    private record ValidRow(String poNo, String poDate, Long supId, Long matId, String matName,
                            String spec, String unit, BigDecimal qty, BigDecimal price,
                            String handler, String projectName, String remark,
                            Map<String, Object> ext, int rowNum) {}

    @Transactional
    public ImportResult importExcel(MultipartFile file) throws IOException {
        List<List<String>> rows = ExcelUtil.readRows(file.getInputStream(), MAX_IMPORT_ROWS);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("Excel 至少需要表头 + 一行数据");
        }
        List<String> headers = rows.get(0);
        Map<String, Integer> colIdx = new HashMap<>();
        for (int c = 0; c < headers.size(); c++) {
            colIdx.put(norm(headers.get(c)), c);
        }
        int idxPo = idx(colIdx, "采购单号");
        int idxDate = idx(colIdx, "日期");
        int idxSupplier = idx(colIdx, "供应商名称");
        if (idxSupplier < 0) {
            idxSupplier = idx(colIdx, "供应商");
        }
        int idxMaterial = idx(colIdx, "物料名称");
        if (idxMaterial < 0) {
            idxMaterial = idx(colIdx, "物料");
        }
        int idxSpec = idx(colIdx, "规格");
        int idxUnit = idx(colIdx, "单位");
        int idxQty = idx(colIdx, "数量");
        int idxPrice = idx(colIdx, "单价");
        int idxHandler = idx(colIdx, "经手人");
        int idxProject = idx(colIdx, "项目名称");
        if (idxProject < 0) {
            idxProject = idx(colIdx, "项目");
        }
        int idxRemark = idx(colIdx, "备注");

        if (idxSupplier < 0) {
            throw new IllegalArgumentException("缺少必需列：供应商");
        }
        if (idxMaterial < 0) {
            throw new IllegalArgumentException("缺少必需列：物料名称");
        }
        if (idxQty < 0) {
            throw new IllegalArgumentException("缺少必需列：数量");
        }

        // 动态字段列：表头命中 doc_field_defs.field_name
        List<Map<String, Object>> fieldDefs = docFieldMapper.listFields(DOC_TYPE);
        Map<Integer, String> dynamicCols = new HashMap<>();
        for (var fd : fieldDefs) {
            int c = colIdx.getOrDefault(norm(String.valueOf(fd.get("field_name"))), -1);
            if (c >= 0) {
                dynamicCols.put(c, String.valueOf(fd.get("field_key")));
            }
        }

        // 逐行校验 → 有效明细
        List<ImportError> errors = new ArrayList<>();
        List<ValidRow> valid = new ArrayList<>();
        int skipped = 0;
        Map<String, Long> supCache = new HashMap<>();
        Map<String, Long> matCache = new HashMap<>();

        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int rowNum = i + 1; // Excel 行号
            String poNo = val(row, idxPo);
            String supplierName = val(row, idxSupplier);
            String materialName = val(row, idxMaterial);
            String qtyStr = val(row, idxQty);
            if (supplierName.isEmpty() && materialName.isEmpty() && qtyStr.isEmpty()) {
                continue; // 空行
            }
            if (supplierName.isEmpty()) {
                errors.add(new ImportError(rowNum, "供应商不能为空"));
                continue;
            }
            if (materialName.isEmpty()) {
                errors.add(new ImportError(rowNum, "物料名称不能为空"));
                continue;
            }
            BigDecimal qty;
            try {
                qty = new BigDecimal(qtyStr);
                if (qty.signum() <= 0) {
                    throw new NumberFormatException();
                }
            } catch (Exception e) {
                errors.add(new ImportError(rowNum, "数量必须为大于 0 的数字"));
                continue;
            }

            // 供应商匹配（前 6 字模糊，仿 PhotoService）
            Long supId = supCache.get(supplierName);
            if (supId == null) {
                var hit = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                        .like(Supplier::getName, supplierName.length() > 6 ? supplierName.substring(0, 6) : supplierName)
                        .last("LIMIT 1"));
                supId = hit == null ? null : hit.getId();
                supCache.put(supplierName, supId);
            }
            if (supId == null) {
                errors.add(new ImportError(rowNum, "供应商未匹配：" + supplierName));
                continue;
            }

            // 物料匹配（名称 + 规格精确）
            String spec = val(row, idxSpec);
            String matKey = materialName + "#" + spec;
            Long matId = matCache.get(matKey);
            if (matId == null) {
                var q = new LambdaQueryWrapper<Material>().eq(Material::getName, materialName);
                if (!spec.isEmpty()) {
                    q.eq(Material::getSpec, spec);
                }
                var hit = materialMapper.selectOne(q.last("LIMIT 1"));
                matId = hit == null ? null : hit.getId();
                matCache.put(matKey, matId);
            }
            if (matId == null) {
                errors.add(new ImportError(rowNum, "物料未匹配：" + materialName + (spec.isEmpty() ? "" : " " + spec)));
                continue;
            }

            // 幂等：poNo 已存在（含逻辑删除残留，po_no UNIQUE 键不分 deleted）→ 跳过（重传天然幂等）
            if (!poNo.isEmpty() && trade.purchaseExists(poNo) != null) {
                skipped++;
                continue;
            }

            Map<String, Object> ext = new HashMap<>();
            for (var e : dynamicCols.entrySet()) {
                String v = val(row, e.getKey());
                if (!v.isEmpty()) {
                    ext.put(e.getValue(), v);
                }
            }
            valid.add(new ValidRow(poNo, val(row, idxDate), supId, matId, materialName, spec,
                    val(row, idxUnit), qty, parsePrice(val(row, idxPrice)),
                    val(row, idxHandler), val(row, idxProject), val(row, idxRemark), ext, rowNum));
        }

        // 归组：poNo 非空且相同 → 并组；空 → 每行自成单
        Map<String, List<ValidRow>> groups = new LinkedHashMap<>();
        for (ValidRow vr : valid) {
            String key = vr.poNo.isEmpty() ? "ROW:" + vr.rowNum : vr.poNo;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(vr);
        }

        // 建单（走公共创建服务；项目按名称匹配）
        int created = 0;
        Map<String, Long> projectCache = new HashMap<>();
        for (var e : groups.entrySet()) {
            List<ValidRow> gl = e.getValue();
            ValidRow first = gl.get(0);
            List<PurchaseCreationService.Line> lines = new ArrayList<>();
            for (ValidRow vr : gl) {
                lines.add(new PurchaseCreationService.Line(vr.matId, vr.matName, vr.spec, vr.unit, "",
                        vr.qty, vr.price));
            }
            Long projectId = null;
            String projectName = first.projectName;
            if (!projectName.isEmpty()) {
                if (projectCache.containsKey(projectName)) {
                    projectId = projectCache.get(projectName);
                } else {
                    var hit = projectMapper.selectOne(new LambdaQueryWrapper<Project>()
                            .eq(Project::getName, projectName).last("LIMIT 1"));
                    projectId = hit == null ? null : hit.getId();
                    projectCache.put(projectName, projectId);
                }
                if (projectId == null) {
                    projectName = "";
                }
            }
            var draft = new PurchaseCreationService.Draft(
                    first.poNo.isEmpty() ? null : first.poNo,
                    first.supId, projectId, projectId == null ? "" : projectName,
                    first.poDate.isEmpty() ? null : first.poDate,
                    first.handler, first.remark, cfg.getLong("default_warehouse", 1L), lines);
            try {
                var result = creationService.create(draft, true);
                if (first.ext != null && !first.ext.isEmpty()) {
                    trade.purchaseUpdateExt((Long) result.get("id"), JSON.writeValueAsString(first.ext));
                }
                created++;
            } catch (Exception ex) {
                errors.add(new ImportError(first.rowNum, "建单失败：" + ex.getMessage()));
            }
        }

        return new ImportResult(rows.size() - 1, valid.size(), errors.size(), created, skipped, errors);
    }

    // ============ 模板 / 导出 ============

    /** 下载导入模板（固定列 + 动态字段列，select 带下拉） */
    public byte[] buildTemplate() throws IOException {
        List<Map<String, Object>> fieldDefs = docFieldMapper.listFields(DOC_TYPE);
        List<String> headers = new ArrayList<>(TEMPLATE_COLS);
        for (var fd : fieldDefs) {
            headers.add(String.valueOf(fd.get("field_name")));
        }
        Map<Integer, List<String>> opts = new HashMap<>();
        for (int i = 0; i < fieldDefs.size(); i++) {
            var fd = fieldDefs.get(i);
            if ("select".equals(String.valueOf(fd.get("field_type")))) {
                opts.put(TEMPLATE_COLS.size() + i, parseOptions(fd.get("options")));
            }
        }
        List<List<String>> rows = new ArrayList<>();
        rows.add(new ArrayList<>(Collections.nCopies(headers.size(), "")));
        return ExcelUtil.toXlsx(headers, rows, opts);
    }

    /** 导出采购单列表（含动态字段列） */
    public byte[] buildExport(String keyword) throws IOException {
        List<Map<String, Object>> fieldDefs = docFieldMapper.listFields(DOC_TYPE);
        List<String> headers = new ArrayList<>(EXPORT_COLS);
        for (var fd : fieldDefs) {
            headers.add(String.valueOf(fd.get("field_name")));
        }
        List<Map<String, Object>> data = trade.purchaseExport(keyword);
        List<List<String>> out = new ArrayList<>();
        for (var r : data) {
            Map<String, Object> ext = parseExt(r.get("ext_json"));
            List<String> line = new ArrayList<>();
            line.add(str(r.get("po_no")));
            line.add(slice(str(r.get("po_date")), 10));
            line.add(str(r.get("supplier_name")));
            line.add(str(r.get("handler")));
            line.add(str(r.get("project_name")));
            line.add(str(r.get("total_quantity")));
            line.add(str(r.get("total_amount")));
            line.add(str(r.get("remark")));
            for (var fd : fieldDefs) {
                Object v = ext.get(String.valueOf(fd.get("field_key")));
                line.add(v == null ? "" : String.valueOf(v));
            }
            out.add(line);
        }
        return ExcelUtil.toXlsx(headers, out, null);
    }

    // ============ 工具 ============

    private int idx(Map<String, Integer> colIdx, String name) {
        return colIdx.getOrDefault(norm(name), -1);
    }

    private String val(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) {
            return "";
        }
        return row.get(idx) == null ? "" : row.get(idx).trim();
    }

    private String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s", "");
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private String slice(String s, int n) {
        return s == null || s.length() <= n ? s : s.substring(0, n);
    }

    private BigDecimal parsePrice(String s) {
        if (s == null || s.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
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
    private Map<String, Object> parseExt(Object o) {
        if (o == null || String.valueOf(o).isBlank() || "null".equals(String.valueOf(o))) {
            return new HashMap<>();
        }
        try {
            return JSON.readValue(String.valueOf(o), Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
