package com.yawei.erp.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * 密码校验工具：
 * 1. Python 旧格式 pbkdf2:sha256:600000$salt$hash（SQLite 迁移过来的老用户）
 * 2. BCrypt 新格式（新系统注册/改密的用户）
 */
public final class PasswordUtils {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private PasswordUtils() {}

    /** 校验密码，兼容两种哈希格式 */
    public static boolean matches(String rawPassword, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        if (storedHash.startsWith("pbkdf2:")) {
            return matchesPbkdf2(rawPassword, storedHash);
        }
        try {
            return BCRYPT.matches(rawPassword, storedHash);
        } catch (Exception e) {
            return false;
        }
    }

    /** 新密码加密（BCrypt） */
    public static String encode(String rawPassword) {
        return BCRYPT.encode(rawPassword);
    }

    /**
     * 校验 Python 格式: pbkdf2:sha256:600000$<salt_hex>$<hash_hex>
     * 对应 Python 代码: hashlib.pbkdf2_hmac('sha256', password, bytes.fromhex(salt), 600000)
     */
    private static boolean matchesPbkdf2(String rawPassword, String stored) {
        try {
            // pbkdf2:sha256:600000$salt$hash
            String body = stored.substring("pbkdf2:".length());
            String[] parts = body.split("\\$", 3);
            if (parts.length != 3) {
                return false;
            }
            String algo = parts[0];            // sha256
            String saltHex = parts[1];
            String hashHex = parts[2];
            int iterations;
            // 兼容 "sha256:600000" 或 "sha256$600000" 分隔
            if (algo.contains(":")) {
                String[] ap = algo.split(":");
                algo = ap[0];
                iterations = Integer.parseInt(ap[1]);
            } else {
                iterations = 600000;
            }
            if (!"sha256".equalsIgnoreCase(algo)) {
                return false;
            }
            // Python 端: salt 是 hex 字符串, 直接 .encode() 作为 PBKDF2 salt 字节
            byte[] salt = saltHex.getBytes(StandardCharsets.UTF_8);
            KeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, 256);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(hash, hexToBytes(hashHex));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }
}
