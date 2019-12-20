package aether.client;

import java.io.*;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import aether.security.EncryptUtil;

import aether.security.RSAKeyPairGenerator;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class ChatClient {

    public static void main(String[] args) {
        Socket sock;
        DataOutputStream outStream;
        DataInputStream inStream;

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        String ip = "localhost";

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
            System.out.println("Received key : " + publicKey);
            String data = "Crypto Test : this is a test message for testing out the RSA encryption algorithm";
            String cryptData = EncryptUtil.encryptToString(data, publicKey);
            outStream.writeUTF(cryptData);
            outStream.flush();
        } catch (IOException e) {
            System.out.println("Unable to retrieve public key!");
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
        }

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
                outStream.writeUTF(input);
                outStream.flush();
                String str = inStream.readUTF();
                System.out.println("Message Received : " + str);
            }
            catch (IOException e){
                System.out.println("Error sending request!");
                break;
            }
        }

        try {
            outStream.close();
            inStream.close();
            sock.close();
        } catch (IOException e){
            System.out.println("Error closing connection!");
        }
    }
}
