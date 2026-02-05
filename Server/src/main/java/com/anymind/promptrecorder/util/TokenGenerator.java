package com.anymind.promptrecorder.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class TokenGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {}

    public static String spaceId() {
        return "spc_" + randomToken(16);
    }

    public static String spaceSecret() {
        return "sec_" + randomToken(24);
    }

    private static String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
