package aether.security;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;

public class AESUtil {
    private static SecretKeySpec keySpec;
    private static byte[] key;

    public static String generateKey() {
        SecureRandom random = new SecureRandom();
        byte[] rndBytes = new byte[128 / 8];
        random.nextBytes(rndBytes);
        return Base64.getEncoder().encodeToString(rndBytes);
    }

    public static void setKey(String secretKey) {
        key = secretKey.getBytes(StandardCharsets.UTF_8);
        keySpec = new SecretKeySpec(key, "AES");
    }

    public static byte[] encrypt(byte[] data, String key) {
        try
        {
            setKey(key);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            return cipher.doFinal(data);
        }
        catch (Exception e)
        {
            System.out.println("Error while encrypting: " + e.toString());
        }
        return null;
    }

    public static String encrypt(String data, String key) {
        return Base64.getEncoder().encodeToString(encrypt(data.getBytes(StandardCharsets.UTF_8), key));
    }

    public static byte[] decrypt(byte[] data, String key) {
        try
        {
            setKey(key);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5PADDING");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(data);
        }
        catch (Exception e)
        {
            System.out.println("Error while decrypting: " + e.toString());
        }
        return null;
    }

    public static String decrypt(String data, String key)
    {
        byte[] decryptedData = decrypt(Base64.getDecoder().decode(data), key);
        if (decryptedData == null)
            return null;
        return new String(decryptedData);
    }
}
