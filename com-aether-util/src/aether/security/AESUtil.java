package aether.security;

import aether.exceptions.FileDecryptionError;
import aether.exceptions.FileEncryptionError;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
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

    private static String readEncryptedFile(String fileName, String key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException {
        String content = null;
        try (FileInputStream fileIn = new FileInputStream(fileName)) {
            byte[] fileIv = new byte[16];
            fileIn.read(fileIv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            setKey(key);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(fileIv));

            try (
                    CipherInputStream cipherIn = new CipherInputStream(fileIn, cipher);
                    InputStreamReader inputReader = new InputStreamReader(cipherIn);
                    BufferedReader reader = new BufferedReader(inputReader)
            ) {

                int read;
                char[] buf = new char[1024];
                StringBuilder result = new StringBuilder();
                while ((read = inputReader.read(buf)) != -1) {
                    String data = String.valueOf(buf, 0, read);
                    result.append(data);
                }
                content = result.toString();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return content;
    }

    private static void writeEncryptedFile(String content, String fileName, String key) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        setKey(key);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] iv = cipher.getIV();

        try (FileOutputStream fileOut = new FileOutputStream(fileName);
             CipherOutputStream cipherOut = new CipherOutputStream(fileOut, cipher)) {
            fileOut.write(iv);
            cipherOut.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readEntireFile(File file) throws IOException {
        int length = (int) file.length();
        char[] buf = new char[1024];
        InputStreamReader fileStream = new InputStreamReader(new FileInputStream(file));
        int read;
        StringBuilder result = new StringBuilder();
        while ((read = fileStream.read(buf)) != -1) {
            String data = String.valueOf(buf, 0, read);
            result.append(data);
        }
        return result.toString();
    }

    private static boolean fileExists(String filename) {
        File f = new File(filename);
        return f.exists() && !f.isDirectory();
    }
}
