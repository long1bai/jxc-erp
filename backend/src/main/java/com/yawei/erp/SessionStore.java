package com.yawei.erp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * JWT 无状态会话（2026-08-01 由内存 Map 升级，企业标准做法）
 *
 * 为什么改：原实现是 ConcurrentHashMap 内存会话，后端/断电重启后所有 token 失效，
 * 全员被迫重新登录。JWT 自包含用户信息 + 签名 + 过期时间，服务端不存会话，
 * 重启后旧 token 依然有效（只要在有效期内），彻底解决"重启即重登"。
 *
 * 安全说明：
 * - HS256 对称签名，密钥持久化在 git 外的文件（见 application.yml app.jwt-secret-file），
 *   首次启动自动生成，重启不变 → token 跨重启有效
 * - 无状态 JWT 无法服务端吊销：destroy() 为 no-op（前端登出时清 localStorage 即可）；
 *   如后续需要"踢人/封号立即生效"，需引入短过期 + 黑名单或改回服务端会话
 * - 过期时间 app.jwt-expire-days 可配（默认 7 天）
 */
@Component
public class SessionStore {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] secret;
    private final long expireSeconds;

    public SessionStore(
            @Value("${app.jwt-secret-file:I:/yawei-erp/jwt_secret.txt}") String secretFile,
            @Value("${app.jwt-expire-days:7}") long expireDays) throws Exception {
        this.secret = loadOrCreateSecret(secretFile);
        this.expireSeconds = expireDays * 86400L;
    }

    public record SessionUser(Long id, String username, String displayName, String role) {}

    /** 签发 JWT（HS256）：header.payload.signature */
    public String create(SessionUser user) {
        long now = System.currentTimeMillis() / 1000;
        String header = b64url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("iss", "yawei-erp");
        claims.put("sub", String.valueOf(user.id()));
        claims.put("username", user.username());
        claims.put("displayName", user.displayName());
        claims.put("role", user.role());
        claims.put("iat", now);
        claims.put("exp", now + expireSeconds);
        String payload = b64url(toJson(claims));
        String sig = sign(header + "." + payload);
        return header + "." + payload + "." + sig;
    }

    /** 校验 JWT：验签 + 验过期，返回用户或 null */
    public SessionUser verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String expected = sign(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = JSON.readValue(payloadJson, Map.class);
            Object exp = claims.get("exp");
            if (!(exp instanceof Number) || System.currentTimeMillis() / 1000 >= ((Number) exp).longValue()) {
                return null;
            }
            Object sub = claims.get("sub");
            if (sub == null) {
                return null;
            }
            String role = claims.get("role") == null ? "user" : String.valueOf(claims.get("role"));
            String username = claims.get("username") == null ? "" : String.valueOf(claims.get("username"));
            String displayName = claims.get("displayName") == null ? "" : String.valueOf(claims.get("displayName"));
            return new SessionUser(Long.valueOf(String.valueOf(sub)), username, displayName, role);
        } catch (Exception e) {
            return null;
        }
    }

    /** 无状态 JWT 无法服务端吊销（签名验证不需要服务端状态）。
     *  保留此方法兼容调用方；登出由前端清理 localStorage 完成。 */
    public void destroy(String token) {
        // no-op：JWT 无状态。如需强吊销，引入短过期 + 黑名单表。
    }

    // ---------- 内部工具 ----------

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret, HMAC_ALGO));
            return b64url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 签名失败", e);
        }
    }

    private static String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String b64url(String s) {
        return b64url(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String toJson(Map<String, Object> claims) {
        try {
            return JSON.writeValueAsString(claims);
        } catch (Exception e) {
            throw new IllegalStateException("JWT claims 序列化失败", e);
        }
    }

    /** 读取密钥文件；不存在则生成 32 字节随机密钥并落盘（重启不变） */
    private static byte[] loadOrCreateSecret(String secretFile) throws Exception {
        Path path = Path.of(secretFile);
        if (Files.exists(path)) {
            return Files.readString(path).trim().getBytes(StandardCharsets.UTF_8);
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, encoded, StandardCharsets.UTF_8);
        return encoded.getBytes(StandardCharsets.UTF_8);
    }
}
