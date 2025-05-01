package com.chatapp.security;

import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class SerpentCipher {
    private static final String ALGORITHM = "Serpent";
    private static final String TRANSFORMATION = "Serpent/CBC/PKCS7Padding";
    private static final int IV_LENGTH = 16; // 128 bits
    private static final int KEY_LENGTH = 16; // 128 bits
    
    // Static shared secret key (in a real application, this should be properly managed)
    private static final byte[] SHARED_KEY = "MySecretKey12345".getBytes();
    private static final SecretKey key = new SecretKeySpec(SHARED_KEY, ALGORITHM);

    static {
        // Add BouncyCastle as a security provider
        Security.addProvider(new BouncyCastleProvider());
    }

    public static byte[] encrypt(byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION, "BC");
        
        // Generate random IV
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        
        // Initialize cipher for encryption
        cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
        
        // Encrypt the data
        byte[] encrypted = cipher.doFinal(plaintext);
        
        // Combine IV and encrypted data
        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        
        return result;
    }

    public static byte[] decrypt(byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION, "BC");
        
        // Extract IV from the beginning of the ciphertext
        byte[] iv = Arrays.copyOfRange(ciphertext, 0, IV_LENGTH);
        byte[] encryptedData = Arrays.copyOfRange(ciphertext, IV_LENGTH, ciphertext.length);
        
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        
        // Initialize cipher for decryption
        cipher.init(Cipher.DECRYPT_MODE, key, ivSpec);
        
        // Decrypt the data
        return cipher.doFinal(encryptedData);
    }
} 