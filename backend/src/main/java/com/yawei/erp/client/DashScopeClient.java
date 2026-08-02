package com.yawei.erp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * DashScope（阿里云百炼）OpenAI 兼容接口客户端。
 * 密钥/模型名/提示词从配置文件读取（ai_config.json / photo_config.json），不外泄。
 * 2026-08-02 产品化改造：模型名可配置（json 里 chat_model/vision_model 优先，缺省回退参数）。
 */
@Component
public class DashScopeClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 调用 OpenAI 兼容 chat/completions，返回首个 content 文本。
     *
     * @param configPath    配置文件路径（ai_config.json / photo_config.json）
     * @param defaultModel  模型名兜底（配置文件里 chat_model/vision_model 缺失时用）
     * @param defaultPrompt 系统提示词兜底（配置文件里 system_prompt 缺失时用）
     * @param userContent   用户消息
     */
    public String chat(String configPath, String defaultModel, String defaultPrompt,
                       Object userContent, Map<String, String> promptVars) throws Exception {
        var cfg = JSON.readValue(Files.readString(Path.of(configPath), StandardCharsets.UTF_8), Map.class);
        String apiUrl = (String) cfg.get("api_url");
        String apiKey = (String) cfg.get("api_key");
        if (apiKey == null) {
            // photo_config 用 vision_ 前缀
            apiKey = (String) cfg.get("vision_api_key");
            apiUrl = apiUrl == null ? (String) cfg.get("vision_api_url") : apiUrl;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("配置文件缺少 API Key: " + configPath);
        }

        // 模型名：配置文件优先（chat_model/vision_model），缺省回退参数
        String model = (String) cfg.get("chat_model");
        if (model == null || model.isBlank()) {
            model = (String) cfg.get("vision_model");
        }
        if (model == null || model.isBlank()) {
            model = defaultModel;
        }

        // 系统提示词：配置文件优先（system_prompt），缺省回退参数；支持 {var} 占位符
        String systemPrompt = (String) cfg.get("system_prompt");
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = defaultPrompt;
        }
        if (promptVars != null) {
            for (var e : promptVars.entrySet()) {
                if (e.getValue() != null) {
                    systemPrompt = systemPrompt.replace("{" + e.getKey() + "}", e.getValue());
                }
            }
        }

        var body = Map.of(
                "model", model,
                "messages", java.util.List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userContent)),
                "temperature", 0.2);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8))
                .timeout(java.time.Duration.ofSeconds(120))
                .build();

        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("DashScope 返回 " + resp.statusCode() + ": "
                    + resp.body().substring(0, Math.min(resp.body().length(), 200)));
        }
        JsonNode root = JSON.readTree(resp.body());
        return root.path("choices").get(0).path("message").path("content").asText("");
    }

    /** 简化调用：无占位符 */
    public String chat(String configPath, String defaultModel, String defaultPrompt, Object userContent) throws Exception {
        return chat(configPath, defaultModel, defaultPrompt, userContent, null);
    }
}
