package com.yawei.erp.controller;

import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;
import java.util.Map;
import com.yawei.erp.common.ApiResponse;
import com.yawei.erp.client.DashScopeClient;
import com.yawei.erp.controller.CatalogController.CatalogMapper;

/**
 * AI 报价助手。
 * 2026-08-02 产品化改造：
 *  - 模型名/提示词从 ai_config.json 读取（chat_model / system_prompt），缺省回退内置默认
 *  - 提示词支持 {company_name} 占位符，由 sys_config.company_name 填充
 *  - 配置沿用 Python 版 ai_config.json（api_url/api_key 外置，不外泄）
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Value("${app.ai-config}")
    private String configPath;

    private final DashScopeClient dash;
    private final CatalogMapper catalogMapper;

    public AiController(DashScopeClient dash, CatalogMapper catalogMapper) {
        this.dash = dash;
        this.catalogMapper = catalogMapper;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatReq req) {
        if (req.message() == null || req.message().isBlank()) {
            return ApiResponse.fail("请输入报价需求");
        }
        try {
            String companyName = catalogMapper.configValue("company_name");
            String reply = dash.chat(configPath, "qwen-plus", DEFAULT_SYSTEM_PROMPT, req.message(),
                    Map.of("company_name", companyName == null ? "" : companyName));
            return ApiResponse.ok(Map.of("reply", reply));
        } catch (Exception e) {
            return ApiResponse.fail("AI 报价服务调用失败：" + e.getMessage());
        }
    }

    /** 内置默认提示词（配置未提供时使用；{company_name} 占位符会被替换） */
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是{company_name}的报价助手。根据客户需求给出合理报价，报价规则：
            1. 材料按当前市场价估算，按规格、材质和单价估算；
            2. 加工费按行业常见费率估算；
            3. 给出"材料费 + 加工费 + 利润"的拆分明细，最后给出单价和数量优惠说明；
            4. 只回答本行业相关报价，其他问题礼貌拒绝。
            回复使用中文，简洁专业。""";

    public record ChatReq(String message) {}
}
