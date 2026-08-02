package com.yawei.erp.security;

/** Token 工具: 从 Authorization 头提取 token ("Bearer xxx" 或裸 token) */
public final class TokenUtils {

    private TokenUtils() {}

    public static String extractToken(String auth) {
        if (auth == null) {
            return null;
        }
        String t = auth.trim();
        if (t.startsWith("Bearer ")) {
            return t.substring(7).trim();
        }
        return t;
    }
}
