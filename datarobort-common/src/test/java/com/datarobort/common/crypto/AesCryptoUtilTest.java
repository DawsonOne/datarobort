package com.datarobort.common.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P5: AES/GCM roundtrip, key sensitivity, and at-rest format checks.
 */
class AesCryptoUtilTest {

    private static final String SECRET = "datarobort-dev-key-2026";

    @Test
    void roundtrip_plainTextSurvives() {
        String cipher = AesCryptoUtil.encrypt("sk-abc123", SECRET);
        assertNotNull(cipher);
        assertNotEquals("sk-abc123", cipher, "encrypted output must differ from plaintext");
        assertEquals("sk-abc123", AesCryptoUtil.decrypt(cipher, SECRET));
    }

    @Test
    void roundtrip_unicodeAndLongText() {
        String plain = "你好，世界！password:p@ss|w0rd<>\"'\\x" + "a".repeat(500);
        assertEquals(plain, AesCryptoUtil.decrypt(AesCryptoUtil.encrypt(plain, SECRET), SECRET));
    }

    @Test
    void emptyAndNull_areReturnedAsIs() {
        assertNull(AesCryptoUtil.encrypt(null, SECRET));
        assertEquals("", AesCryptoUtil.encrypt("", SECRET));
        assertNull(AesCryptoUtil.decrypt(null, SECRET));
        assertEquals("", AesCryptoUtil.decrypt("", SECRET));
    }

    @Test
    void wrongKey_failsToDecrypt() {
        String cipher = AesCryptoUtil.encrypt("secret-material", SECRET);
        assertThrows(IllegalStateException.class,
                () -> AesCryptoUtil.decrypt(cipher, "another-key"));
    }

    @Test
    void randomIv_eachEncryptionDiffers() {
        String p = "same input";
        String c1 = AesCryptoUtil.encrypt(p, SECRET);
        String c2 = AesCryptoUtil.encrypt(p, SECRET);
        assertNotEquals(c1, c2, "IV must be random so equal plaintexts differ");
        assertEquals(p, AesCryptoUtil.decrypt(c1, SECRET));
        assertEquals(p, AesCryptoUtil.decrypt(c2, SECRET));
    }

    @Test
    void format_ivPrefixedBase64() {
        String cipher = AesCryptoUtil.encrypt("x", SECRET);
        byte[] raw = Base64.getDecoder().decode(cipher);
        assertTrue(raw.length > 12, "output must be iv(12B) + ciphertext");
        // IV prefix must differ from a fixed zero IV — i.e. random
        byte[] iv = java.util.Arrays.copyOf(raw, 12);
        assertFalse(java.util.Arrays.equals(iv, new byte[12]), "IV must not be zero-filled");
    }

    @Test
    void tamperedCipher_fails() {
        String cipher = AesCryptoUtil.encrypt("integrity-check", SECRET);
        byte[] raw = Base64.getDecoder().decode(cipher);
        raw[raw.length - 1] ^= 0x01; // flip one bit of the GCM tag / last block
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> AesCryptoUtil.decrypt(tampered, SECRET));
    }

    @Test
    void keyStretching_differsFromRawKey() {
        // The master secret is stretched via SHA-256; a 16-char secret must
        // still yield a valid AES-256 key (no InvalidKeyException).
        String plain = "hello";
        String cipher = AesCryptoUtil.encrypt(plain, "short");
        assertEquals(plain, AesCryptoUtil.decrypt(cipher, "short"));
    }

    @Test
    void utf8Safe_outputIsAscii() {
        String cipher = AesCryptoUtil.encrypt("中文🔐", SECRET);
        assertTrue(StandardCharsets.US_ASCII.newEncoder().canEncode(cipher),
                "Base64 output must be ASCII-safe for storage in VARCHAR columns");
    }
}
