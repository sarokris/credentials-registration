package com.credentials.security;

import com.credentials.exception.CredentialProcessingException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static com.credentials.util.RandomUtil.SECURE_RANDOM;

@UtilityClass
@Slf4j
public class EncryptionUtils {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    // In production, load this 32-byte key from an Environment Variable
    private static final byte[] secretKey = "your-super-secure-32-byte-key-!!".getBytes();


    public static String encrypt(String plainText) {
        byte[] iv = new byte[IV_LENGTH_BYTE];
        SECURE_RANDOM.nextBytes(iv);
        byte[] combined;
        try {
            Cipher cipher = null;
            cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey, "AES"), spec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes());

            // Combine IV and CipherText so we can decrypt it later
            combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |InvalidKeyException
                 | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            log.error("Encryption error: ", e);
            throw new CredentialProcessingException("Encryption failed");
        }
        return Base64.getEncoder().encodeToString(combined);
    }

    public static String decrypt(String encryptedText) {
        byte[] decoded = Base64.getDecoder().decode(encryptedText);

        byte[] iv = new byte[IV_LENGTH_BYTE];
        System.arraycopy(decoded, 0, iv, 0, iv.length);

        byte[] cipherText = new byte[decoded.length - IV_LENGTH_BYTE];
        System.arraycopy(decoded, IV_LENGTH_BYTE, cipherText, 0, cipherText.length);

        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey, "AES"), spec);

            return new String(cipher.doFinal(cipherText));
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException e) {
            log.error("Decryption error: ", e);
            throw new CredentialProcessingException("Decryption failed");

        }
    }
}