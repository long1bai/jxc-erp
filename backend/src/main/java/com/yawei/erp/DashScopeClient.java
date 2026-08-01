package com.yawei.erp;

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
 * 密钥从 Python 版配置文件读取（ai_config.json / photo_config.json），不外泄。
 */
@Component
public class DashScopeClient {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 调用 OpenAI 兼容 chat/completions，返回首个 content 文本 */
    public String chat(String configPath, String model, String systemPrompt, Object userContent) throws Exception {
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
}
