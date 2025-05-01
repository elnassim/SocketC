package com.chatapp.security;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

public class MessageEncryption {
    private final SecretKey serpentKey;
    private final KeyPair rsaKeyPair;
    private PublicKey otherPartyPublicKey;

    public MessageEncryption() throws Exception {
        // Generate Serpent key for symmetric encryption
        this.serpentKey = SerpentEncryption.generateKey();
        
        // Generate RSA key pair for asymmetric encryption
        this.rsaKeyPair = RSAEncryption.generateKeyPair();
    }

    public void setOtherPartyPublicKey(String publicKeyString) throws Exception {
        this.otherPartyPublicKey = RSAEncryption.stringToPublicKey(publicKeyString);
    }

    public String getPublicKeyString() {
        return RSAEncryption.publicKeyToString(rsaKeyPair.getPublic());
    }

    public String encryptMessage(String message) throws Exception {
        if (otherPartyPublicKey == null) {
            throw new IllegalStateException("Other party's public key not set");
        }

        // First encrypt the message with Serpent
        byte[] serpentEncrypted = SerpentEncryption.encrypt(message.getBytes(), serpentKey);
        String serpentEncryptedBase64 = Base64.getEncoder().encodeToString(serpentEncrypted);
        
        // Then encrypt the Serpent key with RSA
        byte[] encryptedKey = RSAEncryption.encrypt(serpentKey.getEncoded(), otherPartyPublicKey);
        String encryptedKeyBase64 = Base64.getEncoder().encodeToString(encryptedKey);
        
        // Return both encrypted key and message
        return encryptedKeyBase64 + ":" + serpentEncryptedBase64;
    }

    public String decryptMessage(String encryptedMessage) throws Exception {
        // Split the message into encrypted key and encrypted content
        String[] parts = encryptedMessage.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid encrypted message format");
        }

        // Decode from Base64
        byte[] encryptedKey = Base64.getDecoder().decode(parts[0]);
        byte[] encryptedContent = Base64.getDecoder().decode(parts[1]);

        // First decrypt the Serpent key using RSA private key
        byte[] decryptedKeyBytes = RSAEncryption.decrypt(encryptedKey, rsaKeyPair.getPrivate());
        SecretKey decryptedSerpentKey = SerpentEncryption.bytesToKey(decryptedKeyBytes);

        // Then decrypt the message using the decrypted Serpent key
        byte[] decryptedContent = SerpentEncryption.decrypt(encryptedContent, decryptedSerpentKey);
        
        return new String(decryptedContent);
    }
} 