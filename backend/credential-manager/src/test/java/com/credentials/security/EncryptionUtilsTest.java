package com.credentials.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EncryptionUtilsTest {

    @Test
    void testEncryptAndDecrypt()  {
        String original = "SensitiveData123!";

        String encrypted = EncryptionUtils.encrypt(original);
        assertNotNull(encrypted);
        assertNotEquals(original, encrypted);

        String decrypted = EncryptionUtils.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    void testDecryptWithInvalidData() {
        String invalidEncrypted = "invalidBase64==";

        assertThrows(Exception.class, () -> EncryptionUtils.decrypt(invalidEncrypted));
    }
}
