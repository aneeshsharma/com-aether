package aether.security;

import aether.exceptions.FileDecryptionError;
import aether.exceptions.FileEncryptionError;

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
        byte[] enc = encrypt(data.getBytes(StandardCharsets.UTF_8), key);
        if (enc == null)
                return null;
        return Base64.getEncoder().encodeToString(enc);
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

    public static void encryptFile(String fileName, String newName, String encKey) throws FileEncryptionError {
        if (fileExists(fileName)) {
            File originalFile = new File(fileName);
            File newFile = new File(newName);

            try (
                    FileOutputStream fileOut = new FileOutputStream(newFile);
                    FileInputStream fileIn = new FileInputStream(originalFile)
                    ) {
                int length = (int) originalFile.length();
                byte[] readBytes = new byte[length];
                fileIn.read(readBytes);

                byte[] encrypted = encrypt(readBytes, encKey);
                if (encrypted == null) {
                    throw new FileEncryptionError("Unable to encrypt");
                }
                fileOut.write(encrypted);
            } catch (IOException e) {
                throw new FileEncryptionError("Unable to Read/Write");
            }
            if (!originalFile.delete())
                throw new FileEncryptionError("Unable to delete redundant files");
        }
    }

    public static void decryptFile(String fileName, String newName, String encKey) throws FileDecryptionError {
        if (fileExists(fileName)) {
            File originalFile = new File(fileName);
            File newFile = new File(newName);

            try (
                    FileOutputStream fileOut = new FileOutputStream(newFile);
                    FileInputStream fileIn = new FileInputStream(originalFile)
            ) {
                int length = (int) originalFile.length();
                byte[] readBytes = new byte[length];
                fileIn.read(readBytes);

                byte[] encrypted = decrypt(readBytes, encKey);
                if (encrypted == null) {
                    throw new FileDecryptionError("Unable to encrypt");
                }
                fileOut.write(encrypted);
            } catch (IOException e) {
                throw new FileDecryptionError("Unable to Read/Write");
            }

            if (!originalFile.delete())
                throw new FileDecryptionError("Unable to delete redundant files");
        }
    }

    private static boolean fileExists(String filename) {
        File f = new File(filename);
        return f.exists() && !f.isDirectory();
    }
}
