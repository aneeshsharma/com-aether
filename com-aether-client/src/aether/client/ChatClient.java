package aether.client;

import java.io.*;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.KeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import aether.data.ChatData;
import aether.exceptions.*;
import aether.security.AESUtil;
import aether.security.RSAKeyPairGenerator;
import aether.security.RSAUtil;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class ChatClient {
    private static String secretKey;
    private static String ip = "localhost";
    private static String dataDir = "/.aether-data/";

    private static ChatData chatData;

    private static String password;

    private static String username, key, privateKey, publicKey;

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
            System.out.print("(DEBUG) User: ");
            String debug_user = stdin.readLine();
            dataDir += debug_user + "/";
            File f = new File(dataDir);
            if (!f.exists())
                f.mkdirs();
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
            System.out.println("couldn't retrieve credentials!");
            return;
        }

        try {
            chatData = new ChatData("ChatData", dataDir + "chatData.db", password);
        } catch (ClassNotFoundException | SQLException | KeyException | FileDecryptionError e) {
            System.out.println("Unable to retrieve chat data! Exiting...");
            return;
        }

        String loginMsg = "Cannot authenticate user | Server error";

        try {
            sendData("login:" + username + "," + key);
            loginMsg = receiveData();
        } catch (IOException e) {
            System.out.println("Couldn't contact server!");
        }

        if (!loginMsg.equals("Successfully logged in!")) {
            System.out.println(loginMsg);
            return;
        }

        getUpdates();

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
                processCommand(input);
            } catch (InvalidCommandError e) {
                System.out.println("Invalid command : " + e.getMessage());
            }
        }

        // Close the connection
        try {
            closeConnection();
        } catch (ConnectionError connectionError) {
            System.out.println("Error closing connection! " + connectionError.getMessage());
        }
        try {
            chatData.close();
        } catch (SQLException | FileEncryptionError e) {
            System.out.println("Error closing chat data! " + e.getMessage());
        }
    }

    private static void processCommand(String input) throws InvalidCommandError {
        String request = "nothing:nothing";

        // remove extra whte spaces
        input = input.replaceAll("\\s+", " ");
        input = input.strip();

        if (input.startsWith("/command ")) {
            request = input.substring("/command ".length());
            try {
                sendData(request);
            } catch (IOException e) {
                System.out.println("Unable to contact server!");
            }
        } else if (input.startsWith("/connect ")) {
            String name = input.substring("/connect ".length());
            if (name.equals(username))
                throw new InvalidCommandError("Cannot connect to self");
            request = "connect:" + name;
            String publicKeyReceived = null;
            try {
                sendData(request);
                publicKeyReceived = receiveData();
            } catch (IOException e) {
                System.out.println("Unable to contact server!");
            }

            if (publicKeyReceived == null) {
                System.out.println("Unexpected error occurred!");
                return;
            }

            if (publicKeyReceived.startsWith("NO SUCH USER")) {
                System.out.println("Requested user doesn't exist");
            } else if (publicKeyReceived.startsWith("SERVER ERROR")) {
                System.out.println("Server error!");
            } else {
                String chatKey = AESUtil.generateKey();
                String cryptData;
                try {
                    cryptData = RSAUtil.encryptToString(chatKey, publicKeyReceived);
                } catch (IllegalBlockSizeException | InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException | BadPaddingException e) {
                    e.printStackTrace();
                    System.out.println("Unable to encrypt data!");
                    return;
                }

                String reply = null;
                try {
                    sendData("connection_request:" + name + "," + cryptData);
                    reply = receiveData();
                } catch (IOException e) {
                    System.out.println("Unable to contact server!");
                    return;
                }

                if (reply.equals("CONNECTION REQUESTED")) {
                    try {
                        chatData.newChat(name, chatKey);
                    } catch (SQLException e) {
                        System.out.println("Unable to save key!");
                    }
                } else {
                    System.out.println("Request unsuccessful : " + reply);
                }
            }
        } else if (input.startsWith("/send ")) {
            String send_data = input.substring("/send ".length());
            String[] args = send_data.split(":");
            String to, message;
            try {
                to = args[0];
                message = args[1];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new InvalidCommandError("Format - \"/send <chat_name>:<message>\"");
            }
            try {
                String reply = sendMessage(to, message);
                System.out.println(reply);
            } catch (IOException | SQLException e) {
                System.out.println("Unable to send message!");
            } catch (NoSuchUserError noSuchUserError) {
                throw new InvalidCommandError("No such user:" + to);
            }
        } else if (input.equals("/update")) {
            getUpdates();
        } else if (input.startsWith("/get_messages ")) {
            getUpdates();
            String query = input.substring("/get_messages ".length());
            String[] data = query.split(" ");
            String from;
            int n;
            try {
                from = data[0];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new InvalidCommandError("Format - \"/get_messages <chat_name> <number_of_messages>\"");
            }

            try {
                n = Integer.parseInt(data[1]);
            } catch (ArrayIndexOutOfBoundsException e) {
                n = 10;
            }
            try {
                String messages = chatData.getLastNMessages(from, n);
                System.out.println(messages);
            } catch (SQLException e) {
                System.out.println("Unable to fetch messages!");
            }
        }
    }

    private static String sendMessage(String to, String message) throws SQLException, NoSuchUserError, IOException {
        String chatKey = chatData.getEncryptionKey(to);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String updateData = "message:" + username + "," + message + "," + dtf.format(now);
        String encryptedData = AESUtil.encrypt(updateData, chatKey);
        String request = "update:" + to + "," + encryptedData;
        sendData(request);
        String reply = receiveData();
        if (reply.equals("UPDATE SUCCESSFUL"))
            chatData.addMessage(to, message, username, "SENT", dtf.format(now));
        return reply;
    }

    private static void getUpdates() {
        try {
            sendData("get_updates:" + username);
            int lastId = -1;
            while (true) {
                String update = getUpdateString();
                System.out.println("Received update : " + update);
                if (!update.startsWith("UPDATE:")) {
                    sendData(String.valueOf(lastId));
                    String reply = receiveData();
                    System.out.println("Updates retrieved : " + reply);
                    break;
                }
                lastId = processUpdate(update, lastId);
            }
        } catch (IOException e) {
            System.out.println("Unable to contact server!");
        }
    }

    private static int processUpdate(String update, int last) {
        update = update.substring("UPDATE:".length());
        String[] args = update.split(",");
        int id = Integer.parseInt(args[0]);
        String from = args[1];
        String type = args[2];
        String update_data = args[3];
        String date = args[4];
        if (type.equals("REQUEST")) {
            String chatKey = null;
            try {
                chatKey = RSAUtil.decrypt(update_data, RSAUtil.getPrivateKey(privateKey));
            } catch (IllegalBlockSizeException | InvalidKeyException | BadPaddingException | NoSuchAlgorithmException | NoSuchPaddingException e) {
                e.printStackTrace();
            }
            if (chatKey == null) {
                System.out.println("Unable to decrypt request");
                return last;
            }

            System.out.println("Received chat key : " + chatKey);
            try {
                chatData.newChat(from, chatKey);
            } catch (SQLException e) {
                System.out.println("Unable to create chat!");
                return last;
            }

            return id;
        } else if (type.equals("UPDATE")) {
            String chatKey = null;
            try {
                 chatKey = chatData.getEncryptionKey(from);
            } catch (SQLException e) {
                System.out.println("Error occurred getting encryption key!");
            } catch (NoSuchUserError e) {
                System.out.println("No connection to given user!");
            }

            if (chatKey == null)
                return last;

            String updateQuery = AESUtil.decrypt(update_data, chatKey);
            if (updateQuery == null) {
                System.out.println("Received data can't be decrypted!");
                return last;
            }
            if (updateQuery.startsWith("message:")) {
                String[] updateArgs = updateQuery.substring("message:".length()).split(",");
                String messageAuthor = updateArgs[0];
                String message = updateArgs[1];
                String messageDate = updateArgs[2];
                try {
                    chatData.addMessage(from, message, messageAuthor, "RECEIVED", messageDate);
                } catch (SQLException e) {
                    System.out.println("Unable to store messages!");
                    return last;
                } catch (NoSuchUserError noSuchUserError) {
                    System.out.println("This is never gonna happen. But if it does, congratulations! You have entered a new reality.");
                    return last;
                }
            }
            return id;
        }
        return last;
    }

    private static String getUpdateString() throws IOException {
        String data = receiveData();
        if (!data.startsWith("UPDATE:"))
            return data;
        sendData("NEXT");
        return data;
    }

    private static boolean fileExists(String filename) {
        File f = new File(filename);
        return f.exists() && !f.isDirectory();
    }

    private static void initializeVault() throws VaultError {
        if (!fileExists(dataDir + ".credentials")) {
            try {
                registerNewUser();
            } catch (RegistrationError registrationError) {
                throw new VaultError(registrationError);
            }
            System.out.println("Create new data encryption password");
            try {
                getNewPassword();
            } catch (IOException e) {
                throw new VaultError(e.getMessage());
            }
            String data = username + "\n" + key + "\n" + privateKey + "\n" + publicKey;
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
                privateKey = credentials[2];
                publicKey = credentials[3];
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

    private static void closeConnection() throws ConnectionError {
        try {
            outStream.close();
            inStream.close();
            sock.close();
        } catch (IOException e){
            throw new ConnectionError(e);
        }
    }

    /**
     * Method to register a new user on the local device as well as the server
     */
    private static void registerNewUser() throws RegistrationError {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Register new user");
        System.out.print("Username : ");
        String username = null;
        String usernameExists = "true";
        while (usernameExists.equals("true")) {
            try {
                username = stdin.readLine();
            } catch (IOException e) {
                throw new RegistrationError(e);
            }

            try {
                sendData("check_username:" + username);
                usernameExists = receiveData();
                if (usernameExists.equals("true")) {
                    System.out.println("Username already exists please try a different username");
                }
            } catch (IOException e) {
                throw new RegistrationError(e);
            }
        }

        if (!usernameExists.equals("false")) {
            throw new RegistrationError("Unexpected server error occurred!");
        }

        // Use the same key generator
        String key = AESUtil.generateKey();
        RSAKeyPairGenerator keygen;
        try {
            keygen = new RSAKeyPairGenerator();
        } catch (NoSuchAlgorithmException e) {
            throw new RegistrationError(e);
        }

        privateKey = keygen.getPrivateKeyAsString();
        publicKey = keygen.getPublicKeyAsString();

        String fullName;
        System.out.print("Full Name (optional): ");
        try {
            fullName = stdin.readLine();
            if (fullName.length() <= 0)
                fullName = "N/A";
        } catch (IOException e) {
            fullName = " ";
        }

        String registrationStatus;
        try {
            sendData("register:" + username + "," + key + "," + fullName + "," + publicKey);
            registrationStatus = receiveData();
        } catch (IOException e) {
            throw new RegistrationError(e);
        }

        System.out.println(registrationStatus);
        if (!registrationStatus.equals("Success")) {
            return;
        }
        ChatClient.username = username;
        ChatClient.key = key;
    }
}
