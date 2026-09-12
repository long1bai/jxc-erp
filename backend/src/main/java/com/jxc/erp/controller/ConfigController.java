package com.jxc.erp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jxc.erp.common.ApiResponse;
import com.jxc.erp.controller.CatalogController.CatalogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置管理（2026-08-02 产品化）：管理员在界面修改系统参数 + AI 配置。
 * - GET /api/config/all  -> 全部 sys_config（key/value/remark）
 * - PUT /api/config      -> 批量保存 sys_config（body: {key: value, ...}）
 * - GET /api/config/ai   -> 读 ai_config.json（脱敏 api_key，只回显后4位）
 * - PUT /api/config/ai   -> 写 ai_config.json（含 api_url/api_key/chat_model/vision_model/system_prompt）
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Value("${app.ai-config}")
    private String aiConfigPath;

    private final CatalogMapper mapper;

    public ConfigController(CatalogMapper mapper) {
        this.mapper = mapper;
    }

    /** 全部系统参数 */
    @GetMapping("/all")
    public Map<String, Object> all() {
        List<Map<String, Object>> rows = mapper.allConfig();
        return ApiResponse.ok(Map.of("items", rows));
    }

    /** 批量保存系统参数（body: {"key": "value", ...}） */
    @PutMapping
    public Map<String, Object> save(@RequestBody Map<String, String> body) {
        if (body == null || body.isEmpty()) {
            return ApiResponse.fail("没有要保存的配置");
        }
        int saved = 0;
        for (var e : body.entrySet()) {
            String key = e.getKey();
            String val = e.getValue() == null ? "" : e.getValue().trim();
            // 白名单校验：只允许已知参数键，防注入任意键
            if (!KNOWN_KEYS.contains(key)) {
                continue;
            }
            mapper.upsertConfig(key, val, remarkFor(key));
            saved++;
        }
        return ApiResponse.ok(Map.of("saved", saved));
    }

    /** 读 AI 配置（api_key 脱敏） */
    @GetMapping("/ai")
    public Map<String, Object> getAi() {
        try {
            Map<String, Object> cfg = JSON.readValue(
                    Files.readString(Path.of(aiConfigPath), java.nio.charset.StandardCharsets.UTF_8), Map.class);
            // 脱敏 api_key：只显示后 4 位
            Object key = cfg.get("api_key");
            if (key instanceof String s && s.length() > 4) {
                cfg = new LinkedHashMap<>(cfg);
                cfg.put("api_key", "****" + s.substring(s.length() - 4));
                cfg.put("api_key_set", true);
            }
            return ApiResponse.ok(cfg);
        } catch (Exception e) {
            return ApiResponse.fail("读取 AI 配置失败：" + e.getMessage());
        }
    }

    /** 写 AI 配置（完整覆盖 ai_config.json） */
    @PutMapping("/ai")
    public Map<String, Object> saveAi(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> cfg = new LinkedHashMap<>();
            // 保留 api_url/api_key（key 为空则不覆盖原值；只允许这几个键）
            for (String k : List.of("api_url", "api_key", "chat_model", "vision_model", "system_prompt")) {
                Object v = body.get(k);
                if (v != null && !(v instanceof String s && s.isBlank())) {
                    cfg.put(k, v);
                }
            }
            // api_key 若传的是脱敏占位（****xxxx），保留原值
            Object key = cfg.get("api_key");
            if (key instanceof String s && s.startsWith("****")) {
                // 读旧值保留
                Map<String, Object> old = JSON.readValue(
                        Files.readString(Path.of(aiConfigPath), java.nio.charset.StandardCharsets.UTF_8), Map.class);
                cfg.put("api_key", old.getOrDefault("api_key", ""));
            }
            JSON.writeValue(Path.of(aiConfigPath).toFile(), cfg);
            return ApiResponse.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ApiResponse.fail("保存 AI 配置失败：" + e.getMessage());
        }
    }

    // ============ 已知参数键（白名单） ============

    private static final java.util.Set<String> KNOWN_KEYS = java.util.Set.of(
            // 公司/品牌
            "company_name", "company_address", "company_phone", "system_name",
            // 单号前缀
            "seq_ie", "seq_zz", "seq_cgdd", "seq_th", "seq_rcv", "seq_pay", "seq_inv", "seq_po",
            // 业务参数
            "default_warehouse", "default_material_category", "employee_no_prefix",
            // 报工休息时段
            "break_lunch_start", "break_lunch_end", "break_dinner_start", "break_dinner_end",
            // 功能开关
            "enable_projects"
    );

    private static final Map<String, String> REMARKS = Map.ofEntries(
            Map.entry("company_name", "公司名称（打印抬头/登录页）"),
            Map.entry("company_address", "公司地址"),
            Map.entry("company_phone", "公司电话"),
            Map.entry("system_name", "系统名称（登录页/侧边栏标题）"),
            Map.entry("seq_ie", "收支单号前缀"),
            Map.entry("seq_zz", "转账单号前缀"),
            Map.entry("seq_cgdd", "采购入库单号前缀"),
            Map.entry("seq_th", "采购退货单号前缀"),
            Map.entry("seq_rcv", "收款单号前缀"),
            Map.entry("seq_pay", "付款单号前缀"),
            Map.entry("seq_inv", "发票单号前缀"),
            Map.entry("seq_po", "采购订单号前缀"),
            Map.entry("default_warehouse", "默认仓库id"),
            Map.entry("default_material_category", "自动建物料默认分类"),
            Map.entry("employee_no_prefix", "员工工号前缀"),
            Map.entry("break_lunch_start", "午休开始"),
            Map.entry("break_lunch_end", "午休结束"),
            Map.entry("break_dinner_start", "晚餐开始"),
            Map.entry("break_dinner_end", "晚餐结束"),
            Map.entry("enable_projects", "项目模块开关（1=显示项目/项目成本菜单，0=关闭）")
    );

    private static String remarkFor(String key) {
        return REMARKS.getOrDefault(key, "");
    }
}
