package com.credentials.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MaskingUtil {
    public static String mask(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        int unmasked = 4;
        int maskedLength = value.length() - unmasked;
        return "*".repeat(maskedLength) + value.substring(maskedLength);
    }
}
