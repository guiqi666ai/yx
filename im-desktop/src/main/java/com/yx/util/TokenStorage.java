package com.yx.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class TokenStorage {
    private static final Path TOKEN_FILE = Paths.get(
            System.getProperty("user.home"), ".im", "refresh.token");

    public static void save(String refreshToken) {
        try {
            Files.createDirectories(TOKEN_FILE.getParent());
            // 简单加密（生产环境用 AES + 密钥）
            String encrypted = Base64.getEncoder()
                    .encodeToString(refreshToken.getBytes());
            Files.writeString(TOKEN_FILE, encrypted);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String load() {
        try {
            if (!Files.exists(TOKEN_FILE)) return null;
            String encrypted = Files.readString(TOKEN_FILE);
            return new String(Base64.getDecoder().decode(encrypted));
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear() {
        try {
            Files.deleteIfExists(TOKEN_FILE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}