package com.credentials.util;

import lombok.experimental.UtilityClass;

import java.security.SecureRandom;

@UtilityClass
public class RandomUtil {
    public  static final SecureRandom SECURE_RANDOM = new SecureRandom();
    public static String generateRandomSuffix(int i) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder suffix = new StringBuilder(i);
        for (int j = 0; j < i; j++) {
            suffix.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return suffix.toString();
    }
}
