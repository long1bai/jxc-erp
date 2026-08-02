package com.yawei.erp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yawei.erp.client.DashScopeClient;
import com.yawei.erp.entity.Material;
import com.yawei.erp.entity.Supplier;
import com.yawei.erp.mapper.MaterialMapper;
import com.yawei.erp.mapper.SupplierMapper;
import com.yawei.erp.mapper.SysMapper;
import com.yawei.erp.mapper.TradeMapper;
import com.yawei.erp.dto.PhotoDtos.ConfirmItem;
import com.yawei.erp.util.SequenceUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 拍照入库业务层（DashScope qwen3-vl-plus）。
 * 逻辑自 PhotoController 抽取（2026-08-02 企业化分层重构），行为保持不变。
 */
@Service
public class PhotoService {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Value("${app.photo-config}")
    private String photoConfigPath;

    private final DashScopeClient dash;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final TradeMapper trade;
    private final SysMapper sys;
    private final SequenceUtil seq;

        private final SysConfigService cfg;

public PhotoService(DashScopeClient dash, SupplierMapper supplierMapper,
                        MaterialMapper materialMapper, TradeMapper trade,
                        SysMapper sys, SequenceUtil seq, SysConfigService cfg) {
        this.dash = dash;
        this.cfg = cfg;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.trade = trade;
        this.sys = sys;
        this.seq = seq;
    }

    // ============ 识别 ============

    public Map<String, Object> recognize(String imageBase64) throws Exception {
        String dataUrl = imageBase64.startsWith("data:") ? imageBase64
                : "data:image/jpeg;base64," + imageBase64;
        Object content = List.of(
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)),
                Map.of("type", "text", "text", RECOGNIZE_PROMPT));
        // 2026-08-02 产品化：模型名从 photo_config.json 读（vision_model），提示词从 system_prompt 读，缺省回退内置
        String reply = dash.chat(photoConfigPath, "qwen3-vl-plus", "你是单据识别专家。", content);

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
        return result;
    }

    // ============ 确认入库 ============

    /** 确认核对后的数据 → 生成采购入库单（自动补齐规格/自动新建物料 + 加库存流水） */
    @Transactional
    public Map<String, Object> confirm(Long supplierId, String poDate, String remark,
                                       Long warehouseId,
                                       List<ConfirmItem> items) {
        // 物料归一：匹配到的补齐规格，未匹配到的自动新建
        java.util.List<ConfirmItem> clean = new java.util.ArrayList<>();
        for (var it : items) {
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
                m.setCategory(cfg.get("default_material_category"));
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
            return Map.of("error", "没有可入库的明细（数量都无效）");
        }

        // 生成采购入库单（同 PurchaseController.create 口径）
        String poNo = seq.nextDaily(cfg.get("seq_cgdd"));
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalAmt = BigDecimal.ZERO;
        for (var it : clean) {
            BigDecimal amt = amount(it.quantity(), it.unitPrice());
            totalQty = totalQty.add(it.quantity());
            totalAmt = totalAmt.add(amt);
        }
        String poDate2 = poDate == null || poDate.isBlank() ? today() : poDate;
        trade.purchaseInsert(poNo, supplierId, poDate2, "", totalQty, totalAmt, remark);
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
            trade.movementInsertWh(it.materialId(), warehouseId, "in", "purchase", poId,
                    it.quantity(), before, after, it.unitPrice(), amt, today(), "采购入库#" + poNo);
        }
        return Map.of("id", poId, "poNo", poNo, "created", clean.size());
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
}
