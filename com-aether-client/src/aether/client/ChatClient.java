package aether.client;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;

import aether.data.ChatData;
import aether.exceptions.*;
import aether.security.AESUtil;
import aether.security.RSAUtil;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class ChatClient {
    private static String secretKey;
    private static String ip = "localhost";
    private static String dataDir = "/.aether-data/";

    private static String password;

    private static String username, key;

    private static DataOutputStream outStream;
    private static DataInputStream inStream;

    private static BufferedReader stdin;

    private static Socket sock;

    public static void main(String[] args) {
        String home = System.getProperty("user.home");
        dataDir = home + "/.aether-data/";

        stdin = new BufferedReader(new InputStreamReader(System.in));


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
        try {
            establishSecureConnection();
        } catch (ConnectionError connectionError) {
            System.out.print("Connection error: " + connectionError.getMessage());
            return;
        }

        try {
            initializeVault();
        } catch (VaultError vaultError) {
            vaultError.printStackTrace();
        }

        String loginMsg = "Cannot authenticate user | Server error";

        try {
            sendData("login:" + username + "," + key);
            loginMsg = receiveData();
        } catch (IOException e) {
            System.out.println("Couldn't contact server!");
        }

        System.out.println(loginMsg);

        ChatData chatData = null;
        try {
            chatData = new ChatData("ChatData", "chatDataTest.db", password);
        } catch (ClassNotFoundException | SQLException | KeyException | FileDecryptionError e) {
            e.printStackTrace();
        }

        try {
            assert chatData != null;
            chatData.addMessage("receiver", "hello!", "me", "SENT");
            System.out.println("Chats:\n" + chatData.getAllChats());
            System.out.println("CHat: \n" + chatData.getLastNMessages("receiver", 5));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (!loginMsg.equals("Successfully logged in!"))
            return;

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
            } catch (IOException e) {
                System.out.println("Error sending request!");
                break;
            }
        }

        // Close the connection
        closeConnection();
        try {
            chatData.close();
        } catch (SQLException | FileEncryptionError e) {
            e.printStackTrace();
        }
    }

    private static boolean fileExists(String filename) {
        File f = new File(filename);
        return f.exists() && !f.isDirectory();
    }

    private static void initializeVault() throws VaultError {
        if (!fileExists(dataDir + ".credentials")) {
            registerNewUser(dataDir);
            System.out.println("Create new data encryption password");
            try {
                getNewPassword();
            } catch (IOException e) {
                throw new VaultError(e.getMessage());
            }
            String data = username + "\n" + key;
            String encryptedData = AESUtil.encrypt(data, password);
            try {
                FileOutputStream file = new FileOutputStream(dataDir + ".credentials");
                OutputStreamWriter fileOut = new OutputStreamWriter(file);
                assert encryptedData != null;
                fileOut.write(encryptedData);
                fileOut.close();
            } catch (IOException e) {
                throw new VaultError(e);
            }
        } else {
            String data;
            try {
                System.out.print("Password: ");
                password = stdin.readLine();
                password = padRight(password, 16);
                InputStreamReader fileIn = new InputStreamReader(new FileInputStream(dataDir + ".credentials"));
                int read;
                char[] buf = new char[128];
                StringBuilder result = new StringBuilder();
                while ((read = fileIn.read(buf)) != -1) {
                    String readData = String.valueOf(buf, 0, read);
                    result.append(readData);
                }
                data = result.toString();
            } catch (IOException e) {
                throw new VaultError(e);
            }

            String decryptedData = AESUtil.decrypt(data, password);

            try {
                assert decryptedData != null;
                String[] credentials = decryptedData.split("\n");
                username = credentials[0];
                key = credentials[1];
            } catch (NullPointerException | IndexOutOfBoundsException e) {
                throw new VaultError(e);
            }
        }
    }

    private static void getNewPassword() throws IOException {
        String passwordRead, passwordConfirm;
        while (true) {
            // To read password properly
            //passwordRead = new String(console.readPassword("New User Enter a password: "));
            //String passwordConfirm = new String(console.readPassword("Confirm Password: "));
            System.out.print("Enter a password: ");
            passwordRead = stdin.readLine();
            if (passwordRead.length() > 16) {
                System.out.println("Password cannot be larger than 16 chatacters!");
                continue;
            }
            System.out.print("Confirm Password: ");
            passwordConfirm = stdin.readLine();
            if (passwordRead.equals(passwordConfirm))
                break;
            else
                System.out.println("Passwords didn't match!");
        }
        password = passwordRead;
        password = padRight(password, 16);

    }

    public static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
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
        return AESUtil.decrypt(encryptedData, secretKey);
    }

    private static void establishSecureConnection() throws ConnectionError {
        try{
            sock = new Socket(ip, 7200);
            inStream = new DataInputStream(sock.getInputStream());
            outStream = new DataOutputStream(sock.getOutputStream());
        } catch (IOException e) {
            throw new ConnectionError("Cannot connect to server");
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

    /**
     * Method to register a new user on the local device as well as the server
     * @param dataDir The directory where all user data is to be stored
     */
    private static void registerNewUser(String dataDir) {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Register new user");
        System.out.print("Username : ");
        String username = null;
        String usernameExists = "true";
        while (usernameExists.equals("true")) {
            try {
                username = stdin.readLine();
            } catch (IOException e) {
                System.out.println("Error reading username");
            }

            try {
                sendData("check_username:" + username);
                usernameExists = receiveData();
                if (usernameExists.equals("true")) {
                    System.out.println("Username already exists please try a different username");
                }
            } catch (IOException e) {
                System.out.println("Unable to contact server please try again later");
            }
        }

        if (!usernameExists.equals("false")) {
            System.out.println("Unexpected error occurred!");
            return;
        }

        // Use the same key generator
        String key = AESUtil.generateKey();

        String fullName;
        System.out.print("Full Name (optional): ");
        try {
            fullName = stdin.readLine();
            if (fullName.length() <= 0)
                fullName = "N/A";
        } catch (IOException e) {
            fullName = " ";
        }

        String registrationStatus = "Failed";
        try {
            sendData("register:" + username + "," + key + "," + fullName);
            registrationStatus = receiveData();
        } catch (IOException e) {
            System.out.println("Unable to contact server while registration!");
        }

        System.out.println(registrationStatus);
        if (!registrationStatus.equals("Success")) {
            return;
        }
        ChatClient.username = username;
        ChatClient.key = key;
    }
}
