package aether.client;

import java.io.*;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import aether.security.AESUtil;
import aether.security.RSAUtil;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class ChatClient {
    private static String secretKey;
    private static String ip = "localhost";

    private static DataOutputStream outStream;
    private static DataInputStream inStream;

    private static Socket sock;

    public static void main(String[] args) {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        try {
            System.out.print("Server address (leave blank to use default): ");
            ip = stdin.readLine();
            if (ip.length() == 0) {
                ip = "localhost";
                System.out.println("Using localhost");
            }
        } catch (IOException e) {
            System.out.println("Using default server");
        }

        // Encrypt the connection pipe
        establishSecureConnection();

        while (true) {
            String input = null;
            try {
                System.out.print(">>> ");
                input = stdin.readLine();
            } catch (IOException e) {
                System.out.println("Error occurred while taking user input!");
            }

            if (input == null)
                continue;

            if (input.equals("exit"))
                break;

            try {
                sendData(input);
                String str = receiveData();
                System.out.println("Message Received : " + str);
            }
            catch (IOException e){
                System.out.println("Error sending request!");
                break;
            }
        }

        // Close the connection
        closeConnection();
    }

    private static void sendData(String data) throws IOException {
        String encryptedData = AESUtil.encrypt(data, secretKey);
        if (encryptedData == null)
            return; // Add something though
        outStream.writeUTF(encryptedData);
        outStream.flush();
    }

    private static String receiveData() throws IOException {
        String encryptedData = inStream.readUTF();
        String decryptedData = AESUtil.decrypt(encryptedData, secretKey);
        return decryptedData;
    }

    private static void establishSecureConnection() {
        try{
            sock = new Socket(ip, 7200);
            inStream = new DataInputStream(sock.getInputStream());
            outStream = new DataOutputStream(sock.getOutputStream());
        } catch (IOException e){
            System.out.println("Error connecting to server!!\n"+e);
            return;
        }

        try {
            String publicKey = inStream.readUTF();
            secretKey = AESUtil.generateKey();
            String cryptData = RSAUtil.encryptToString(secretKey, publicKey);
            outStream.writeUTF(cryptData);
            outStream.flush();
        } catch (IOException e) {
            System.out.println("Unable to retrieve public key!");
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }
    }

    private static void closeConnection() {
        try {
            outStream.close();
            inStream.close();
            sock.close();
        } catch (IOException e){
            System.out.println("Error closing connection!");
        }
    }
}
