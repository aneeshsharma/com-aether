package server;

import java.net.*;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

import aether.security.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class ChatClientHandler implements Runnable{
    private Socket handle;
    private String processId;

    private final String JDBC_DRIVER = "org.mariadb.jdbc.Driver";
    private final String DB_URL = "jdbc:mysql://localhost/test";

    private final String USER = "joe";
    private final String PASS = "joemama";

    private Connection dbConn;
    private Statement dbQuery;

    private String secretKey;

    private DataInputStream inStream;
    private DataOutputStream outStream;

    private boolean loggedIn = false;
    private String user;

    ChatClientHandler(Socket sock){
        this(sock, sock.getRemoteSocketAddress().toString());
    }

    ChatClientHandler(Socket sock, String processId) {
        handle = sock;
        this.processId = processId;
    }

    public void run(){
        log("Initializing connection...");

        initializeConnection();

        log("Securing connection...");

        establishSecureConnection();

        log("Secure Connection Established!");

        connectToDBServer();

        log("Database connected!");

        // Request handle loop
        while (true) {
            // Receive Query
            String request;
            try {
                request = receiveData();
                log("Query Received : " + request);
            } catch (EOFException eof) {
                log("Client Disconnected!");
                break;
            } catch (IOException e) {
                log("An IO Exception Occurred!");
                break;
            }

            String function;
            String args;

            // Separate request function and arguments
            try {
                function = request.substring(0, request.indexOf(':'));
                args = request.substring(request.indexOf(':') + 1);
            } catch (Exception e){
                log("Invalid request format : " + request + "\n" + e);
                try {
                    sendData("Invalid Query");
                } catch (IOException ioe) {
                    break;
                }
                continue;
            }

            // Process the request
            String result;
            switch (function){
                case "login":
                    result = login(args);
                    break;
                case "check_username":
                    result = userExists(args);
                    break;
                case "register":
                    result = registerUser(args);
                    break;
                case "search":
                    result = search(args);
                    break;
                case "send":
                    result = send(args);
                    break;
                default:
                    log("Unknown function " + function + " | Skipped");
                    result = "Invalid Query!!";
            }

            // Return result to client
            try {
                sendData(result);
            } catch (IOException e) {
                log("Unable to send result to client!");
            }
        }

        log("Closing connection...");
        closeConnection();
    }

    private String receiveData() throws IOException {
        String encryptedData = inStream.readUTF();
        return AESUtil.decrypt(encryptedData, secretKey);
    }

    private void sendData(String data) throws IOException {
        String encryptedData = AESUtil.encrypt(data, secretKey);
        outStream.writeUTF(encryptedData);
        outStream.flush();
    }

    private void initializeConnection() {
        try {
            inStream = new DataInputStream(handle.getInputStream());
            outStream = new DataOutputStream(handle.getOutputStream());
        }
        catch (IOException e){
            log("Error occurred accepting the given request!\n" + e);
            try {
                handle.close();
            } catch (IOException ex) {
                log("Unable to close connection!");
            }
        }
    }

    private void establishSecureConnection() {
        RSAKeyPairGenerator keygen = null;

        try {
            keygen = new RSAKeyPairGenerator();
        } catch (NoSuchAlgorithmException e) {
            log("RSA Algorithm not found!\nSecure Connection cannot be established");
        }

        if (keygen == null) {
            try {
                outStream.writeUTF("Server Error : Secure Connection cannot be established");
                outStream.flush();
            } catch (IOException e) {
                log("Couldn't contact client. Exiting...");
            }

            try {
                inStream.close();
                outStream.close();
                handle.close();
            } catch (IOException e) {
                log("Unable to close socket and streams!");
            }

            log("Keygen null. Exiting...");

            return;
        }

        log("Public key generated : " + keygen.getPublicKeyAsString());

        try {
            outStream.writeUTF(keygen.getPublicKeyAsString());
            outStream.flush();
            String cryptData = inStream.readUTF();
            secretKey = RSAUtil.decrypt(cryptData, keygen.getPrivateKey());
        } catch (IOException e) {
            System.out.println("Unable to contact client!!");
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException | IllegalBlockSizeException e) {
            e.printStackTrace();
        }
    }

    private void connectToDBServer() {
        dbConn = null;
        dbQuery = null;

        try {
            Class.forName(JDBC_DRIVER);
            log("Connecting to database");
            dbConn = DriverManager.getConnection(DB_URL, USER, PASS);
            dbQuery = dbConn.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeConnection() {
        try {
            inStream.close();
            outStream.close();
            handle.close();
        } catch (IOException e) {
            log("Unable to close socket and streams!");
        }

        try {
            if (dbQuery != null)
                dbQuery.close();
            if (dbConn != null)
                dbConn.close();
        } catch (SQLException e){
            e.printStackTrace();
        }
    }

    private void log(String msg) {
        System.out.println(processId + "\t|\t" + msg);
    }

    private String login(String args) {
        String[] credentials = args.split(",");
        String username = credentials[0];
        String key = credentials[1];
        String sql = "SELECT * from users WHERE username='" + username + "'";
        ResultSet dbRes;
        try {
            dbRes = dbQuery.executeQuery(sql);
        } catch (SQLException e) {
            log("Error executing query!");
            return "Server Error";
        }
        try {
            if (getResultSize(dbRes) != 1) {
                return "Invalid user";
            } else if (dbRes.next() && dbRes.getString("user_key").equals(key)) {
                loggedIn = true;
                this.user = username;
                log("User : " + user +" logged in");
                return "Successfully logged in!";
            }
        } catch (SQLException e) {
            log("Error logging user in : " + username);
            return "Server error | SQL Error";
        }

        return "Key is corrupt";
    }

    private int getResultSize(ResultSet resultSet) throws SQLException {
        int size = 0;
        resultSet.last();
        size = resultSet.getRow();
        resultSet.beforeFirst();
        return size;
    }

    private String userExists(String args) {
        String sql = "SELECT * from users WHERE username='" + args +"'";
        ResultSet dbRes = null;
        try {
            dbRes = dbQuery.executeQuery(sql);
            if (getResultSize(dbRes) >= 1) {
                return "true";
            }
        } catch (SQLException e) {
            return "undefined";
        }
        return "false";
    }

    private String registerUser(String args) {
        String[] credentials = args.split(",");
        String username = credentials[0];
        String key = credentials[1];
        String fullName = credentials[2];
        log("Login Key : " + key);
        try {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDateTime now = LocalDateTime.now();
            String sql = "INSERT INTO users (username, dateJoined, fullName, user_key) VALUES ('" + username + "', '" + dtf.format(now) + "', '" + fullName + "', '" + key + "')";
            dbQuery.executeQuery(sql);
        } catch (SQLException e) {
            return "Failed | " + e.toString();
        }

        return "Success";
    }

    private String search(String args){
        StringBuilder res = new StringBuilder("An unexpected error occurred!!");

        try {
            String sql = "SELECT * from users WHERE username='" + args +"' or fullName='" + args + "'";
            ResultSet dbRes = dbQuery.executeQuery(sql);

            res = new StringBuilder("Names -\n");
            while (dbRes.next()) {
                String name = dbRes.getString("fullName");
                int id = dbRes.getInt("id");
                res.append(id).append(" : ").append(name).append("\n");
            }
            dbRes.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return res.toString();
    }

    private String send(String args){
        return "Send not implemented";
    }
}
