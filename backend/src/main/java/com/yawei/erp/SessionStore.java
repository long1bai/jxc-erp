package com.yawei.erp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单会话存储（进程内存，重启失效）
 * token = 随机 32 字节 base64
 */
@Component
public class SessionStore {

    private final Map<String, SessionUser> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public record SessionUser(Long id, String username, String displayName, String role) {}

    /** 创建会话，返回 token */
    public String create(SessionUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, user);
        return token;
    }

    /** 校验 token，返回用户或 null */
    public SessionUser verify(String token) {
        if (token == null) {
            return null;
        }
        return sessions.get(token);
    }

    public void destroy(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
