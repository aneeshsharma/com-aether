package aether.client;

import java.io.*;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

import aether.data.ChatData;
import aether.exceptions.ConnectionError;
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

    private static Socket sock;

    public static void main(String[] args) {
        String home = System.getProperty("user.home");
        dataDir = home + "/.aether-data/";

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
        try {
            establishSecureConnection();
        } catch (ConnectionError connectionError) {
            System.out.print("Connection error: " + connectionError.getMessage());
            return;
        }

        initializeVault();

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
            chatData = new ChatData("ChatData", "chatDataTest.db");
        } catch (ClassNotFoundException | SQLException e) {
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
    }

    private static void initializeVault() {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        File credentialsFile = new File(dataDir + ".credentials");

        BufferedReader credIn;
        try {
            InputStreamReader fileStream = new InputStreamReader(new FileInputStream(credentialsFile));
            credIn = new BufferedReader(fileStream);
        } catch (FileNotFoundException e) {
            registerNewUser(dataDir);
            try {
                InputStreamReader fileStream = new InputStreamReader(new FileInputStream(credentialsFile));
                credIn = new BufferedReader(fileStream);
            } catch (FileNotFoundException e2) {
                System.out.println("Registration failed!");
                return;
            }
        }

        try {
            System.out.print("Password: ");
            password = stdin.readLine();
        } catch (IOException e) {
            System.out.println("Cannot read password!");
        }

        username = null;
        key = null;
        try {
            username = credIn.readLine();
            key = credIn.readLine();
        } catch (Exception e) {
            System.out.println("Error fetching credentials!");
        }
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

        OutputStream fileOut = null;
        File credentialFile;
        try {
            credentialFile = new File(dataDir + ".credentials");
            fileOut = new FileOutputStream(credentialFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            assert fileOut != null;
            fileOut.write((username + "\n" + key).getBytes());
            fileOut.flush();
        } catch (IOException e) {
            System.out.println("Unable to save credentials!");
        }

        try {
            fileOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
