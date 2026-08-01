package com.yawei.erp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 拍照入库（DashScope qwen3-vl-plus，配置沿用 Python 版 photo_config.json）
 *
 * 闭环：拍照/选图 → 识别（结构化：供应商+明细）→ 前端核对编辑 → 确认入库（生成采购单+库存流水）
 */
@RestController
@RequestMapping("/api/photo")
public class PhotoController {

    private static final String CONFIG_PATH = "I:/yawei-erp/photo_config.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DashScopeClient dash;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final TradeMapper trade;
    private final SysMapper sys;
    private final SequenceUtil seq;

    public PhotoController(DashScopeClient dash, SupplierMapper supplierMapper,
                           MaterialMapper materialMapper, TradeMapper trade,
                           SysMapper sys, SequenceUtil seq) {
        this.dash = dash;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.trade = trade;
        this.sys = sys;
        this.seq = seq;
    }

    // ============ 识别 ============

    @PostMapping("/recognize")
    public Map<String, Object> recognize(@RequestBody PhotoReq req) {
        if (req.imageBase64() == null || req.imageBase64().isBlank()) {
            return ApiResponse.fail("请上传图片");
        }
        try {
            String dataUrl = req.imageBase64().startsWith("data:") ? req.imageBase64()
                    : "data:image/jpeg;base64," + req.imageBase64();
            Object content = List.of(
                    Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                    Map.of("type", "text", "text", RECOGNIZE_PROMPT));
            String reply = dash.chat(CONFIG_PATH, "qwen3-vl-plus", "你是送货单识别专家。", content);

            // 解析 JSON（容错：剥离 ``` 包裹、截取首个 { 到末尾 }）
            Map<String, Object> parsed = parseJson(reply);
            String supplierName = str(parsed.get("supplier_name"));
            String docNo = str(parsed.get("doc_no"));
            String docDate = str(parsed.get("doc_date"));

            // 供应商模糊匹配（前6字）
            Long supplierId = null;
            String matchedName = supplierName;
            if (!supplierName.isBlank()) {
                var hit = supplierMapper.selectOne(new LambdaQueryWrapper<Supplier>()
                        .like(Supplier::getName, supplierName.length() > 6 ? supplierName.substring(0, 6) : supplierName)
                        .last("LIMIT 1"));
                if (hit != null) {
                    supplierId = hit.getId();
                    matchedName = hit.getName();
                }
            }

            Object items = parsed.get("items");
            if (!(items instanceof List)) {
                items = List.of();
            }
            // Map.of 不允许 null 值，供应商未匹配时 supplierId 为 null → 用 HashMap
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("supplierId", supplierId);
            result.put("supplierName", supplierId != null ? matchedName : supplierName);
            result.put("docNo", docNo);
            result.put("docDate", docDate);
            result.put("items", items);
            result.put("raw", reply);
            return ApiResponse.ok(result);
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

        // 物料归一：匹配到的补齐规格，未匹配到的自动新建
        java.util.List<ConfirmItem> clean = new java.util.ArrayList<>();
        for (var it : req.items()) {
            if (it.quantity() == null || it.quantity().signum() <= 0) {
                continue;
            }
            String name = it.name() == null ? "" : it.name().trim();
            Long mid = it.materialId();
            if (mid != null) {
                Material m = materialMapper.selectById(mid);
                if (m == null) {
                    mid = null;
                } else {
                    // AI 识别了规格但库中为空 → 自动补齐
                    String spec = it.spec() == null ? "" : it.spec().trim();
                    if (!spec.isBlank() && isBlank(m.getSpec())) {
                        m.setSpec(spec);
                        materialMapper.updateById(m);
                    }
                }
            }
            if (mid == null && !name.isBlank()) {
                // 自动创建新物料（code 留空，category 默认原材料）
                Material m = new Material();
                m.setName(name);
                m.setSpec(it.spec() == null ? "" : it.spec().trim());
                m.setUnit(it.unit() == null ? "" : it.unit().trim());
                m.setCategory("原材料");
                if (it.unitPrice() != null && it.unitPrice().signum() > 0) {
                    m.setPurchasePrice(it.unitPrice());
                }
                materialMapper.insert(m);
                mid = m.getId();
            }
            if (mid == null) {
                continue;
            }
            clean.add(new ConfirmItem(mid, name, it.spec(), it.unit(), it.quantity(), it.unitPrice()));
        }
        if (clean.isEmpty()) {
            return ApiResponse.fail("没有可入库的明细（数量都无效）");
        }

        // 生成采购入库单（同 PurchaseController.create 口径）
        String poNo = seq.nextDaily("CGDD");
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (var it : clean) {
            BigDecimal amt = amount(it.quantity(), it.unitPrice());
            totalQty = totalQty.add(it.quantity());
            totalAmt = totalAmt.add(amt);
        }
        String poDate = req.poDate() == null || req.poDate().isBlank() ? today() : req.poDate();
        trade.purchaseInsert(poNo, req.supplierId(), poDate, totalQty, totalAmt, req.remark());
        Long poId = trade.lastPurchaseId(poNo);
        int sort = 0;
        for (var it : clean) {
            Material m = materialMapper.selectById(it.materialId());
            String mName = m == null ? it.name() : m.getName();
            String mSpec = m == null ? it.spec() : m.getSpec();
            String mUnit = m == null ? it.unit() : m.getUnit();
            BigDecimal amt = amount(it.quantity(), it.unitPrice());
            trade.purchaseItemInsert(poId, it.materialId(), mName, mSpec, mUnit,
                    it.quantity(), it.unitPrice(), amt, sort++);
            BigDecimal before = currentStock(it.materialId());
            BigDecimal after = before.add(it.quantity());
            trade.movementInsertWh(it.materialId(), req.warehouseId(), "in", "purchase", poId,
                    it.quantity(), before, after, it.unitPrice(), amt, today(), "采购入库#" + poNo);
        }
        return ApiResponse.ok(Map.of("id", poId, "poNo", poNo, "created", clean.size()));
    }

    // ============ 工具 ============

    private BigDecimal currentStock(Long materialId) {
        BigDecimal v = sys.currentStock(materialId);
        return v == null ? BigDecimal.ZERO : v;
    }

    private BigDecimal amount(BigDecimal qty, BigDecimal price) {
        if (qty == null || price == null) {
            return BigDecimal.ZERO;
        }
        return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    private String today() {
        return java.time.LocalDate.now().toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseJson(String text) throws Exception {
        String t = text == null ? "" : text.trim();
        t = t.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        int s = t.indexOf('{');
        int e = t.lastIndexOf('}');
        if (s >= 0 && e > s) {
            t = t.substring(s, e + 1);
        }
        return JSON.readValue(t, Map.class);
    }

    private static final String RECOGNIZE_PROMPT = """
            你是一个送货单识别专家。请从这张送货单图片中提取所有物料明细信息，以JSON格式返回（仅返回JSON，不要其他文字）：
            {
              "supplier_name": "供应商名称",
              "doc_no": "单据编号/送货单号",
              "doc_date": "单据日期(YYYY-MM-DD格式)",
              "items": [
                {
                  "name": "物料名称/货物型号（精确照抄表格中的型号列）",
                  "spec": "规格型号",
                  "unit": "单位",
                  "quantity": 数量(只取数字),
                  "unit_price": 单价
                }
              ]
            }
            关键要求：
            1. 表格中【每一行物料】都要提取，有多少行就输出多少条
            2. 品名/型号/规格要精确照印刷体抄写
            3. 数量只取纯数字，如果没有单价就用0
            4. 日期转换成YYYY-MM-DD格式
            5. 识别不清的字段填空字符串，不要编造""";

    public record PhotoReq(String imageBase64) {}

    public record ConfirmItem(Long materialId, String name, String spec, String unit,
                              BigDecimal quantity, BigDecimal unitPrice) {}

    public record ConfirmReq(Long supplierId, String poDate, String remark, Long warehouseId,
                             List<ConfirmItem> items) {}
}
