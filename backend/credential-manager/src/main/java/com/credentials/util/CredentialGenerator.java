package com.credentials.util;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;
import java.util.Base64;

@UtilityClass
public class CredentialGenerator {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    public static String generateClientSecret() {
        // 32 bytes = 256 bits of entropy (Industry Standard)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        // Using UrlEncoder to ensure the secret can be safely passed in headers/URLs
        return base64Encoder.encodeToString(randomBytes);
    }
}