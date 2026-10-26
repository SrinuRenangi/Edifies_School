package com.edifies.attendance.modules.backup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

@Service
public class BackupEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(BackupEncryptionService.class);
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    @Value("${attendance.backup.encryption-enabled:false}")
    private boolean defaultEncryptionEnabled;

    @Value("${attendance.backup.encryption-key:}")
    private String defaultEncryptionKey;

    public boolean isEncryptionEnabled() {
        return defaultEncryptionEnabled && defaultEncryptionKey != null && !defaultEncryptionKey.isBlank();
    }

    public void encrypt(InputStream in, OutputStream out, String customKey) throws Exception {
        String keyStr = (customKey != null && !customKey.isBlank()) ? customKey : defaultEncryptionKey;
        if (keyStr == null || keyStr.isBlank()) {
            throw new IllegalArgumentException("Backup encryption key cannot be null or empty.");
        }

        SecretKey secretKey = deriveKey(keyStr);
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        // Write IV as prefix
        out.write(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        try (CipherOutputStream cipherOut = new CipherOutputStream(out, cipher)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                cipherOut.write(buffer, 0, read);
            }
            cipherOut.flush();
        }
        log.debug("Successfully encrypted backup stream using AES-256-GCM.");
    }

    public void decrypt(InputStream in, OutputStream out, String customKey) throws Exception {
        String keyStr = (customKey != null && !customKey.isBlank()) ? customKey : defaultEncryptionKey;
        if (keyStr == null || keyStr.isBlank()) {
            throw new IllegalArgumentException("Backup decryption key is required but was not provided or configured.");
        }

        SecretKey secretKey = deriveKey(keyStr);
        byte[] iv = new byte[GCM_IV_LENGTH];
        int ivRead = in.read(iv);
        if (ivRead != GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Corrupt encrypted backup archive: could not read encryption IV.");
        }

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        try (CipherInputStream cipherIn = new CipherInputStream(in, cipher)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = cipherIn.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
        log.debug("Successfully decrypted backup stream using AES-256-GCM.");
    }

    private SecretKey deriveKey(String password) throws Exception {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = sha256.digest(password.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(Arrays.copyOf(keyBytes, 32), "AES");
    }
}
