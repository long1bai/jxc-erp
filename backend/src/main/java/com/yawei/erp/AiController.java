package com.yawei.erp;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** AI 报价助手（DashScope qwen-plus，配置沿用 Python 版 ai_config.json） */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private static final String CONFIG_PATH = "I:/yawei-erp/ai_config.json";

    private final DashScopeClient dash;

    public AiController(DashScopeClient dash) {
        this.dash = dash;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatReq req) {
        if (req.message() == null || req.message().isBlank()) {
            return ApiResponse.fail("请输入报价需求");
        }
        try {
            String reply = dash.chat(CONFIG_PATH, "qwen-plus", SYSTEM_PROMPT, req.message());
            return ApiResponse.ok(Map.of("reply", reply));
        } catch (Exception e) {
            return ApiResponse.fail("AI 报价服务调用失败：" + e.getMessage());
        }
    }

    private static final String SYSTEM_PROMPT = """
            你是示例公司有限公司的线材报价助手。公司主营电线、线缆及线材加工（裁线、打端子、装五金、装夹子壳等）。
            根据客户需求给出合理报价，报价规则：
            1. 材料按当前市场价估算，线材按线规（AWG）、材质（锡铜/红铜/铜包铝）和单价（元/米）估算；
            2. 加工费：裁线 0.02-0.05 元/根，打端子 0.03-0.08 元/个，装五金 0.05-0.15 元/个，装夹子壳 0.1-0.3 元/个；
            3. 给出"材料费 + 加工费 + 利润"的拆分明细，最后给出单价（元/个或元/米）和数量优惠说明；
            4. 只回答线材相关报价，其他问题礼貌拒绝。
            回复使用中文，简洁专业。""";

    public record ChatReq(String message) {}
}
