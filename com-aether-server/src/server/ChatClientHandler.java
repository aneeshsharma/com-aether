package server;

import java.net.*;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.*;

import aether.security.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class ChatClientHandler implements Runnable{
    private Socket handle;

    private final String JDBC_DRIVER = "org.mariadb.jdbc.Driver";
    private final String DB_URL = "jdbc:mysql://localhost/test";

    private final String USER = "joe";
    private final String PASS = "joemama";

    private Connection dbConn;
    private Statement dbQuery;

    private String secretKey;

    private DataInputStream inStream;
    private DataOutputStream outStream;

    public ChatClientHandler(Socket sock){
        handle = sock;
    }

    public void run(){
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
            return;
        }

        log("Initializing RSA...");

        RSAKeyPairGenerator keygen = null;

        try {
            keygen = new RSAKeyPairGenerator();
        } catch (NoSuchAlgorithmException e) {
            log("RSA Algorithm not found!\nSecure Connection cannot be established");
            keygen = null;
        }

        log("Public key generated : " + keygen.getPublicKeyAsString());

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

        log("Secure Connection Established!");

        dbConn = null;
        dbQuery = null;

        try {
            Class.forName(JDBC_DRIVER);

            log("Connecting to database");
            dbConn = DriverManager.getConnection(DB_URL, USER, PASS);
            dbQuery = dbConn.createStatement();
        } catch (Exception e) {
            e.printStackTrace();
        }

        log("Database connected!");

        while (true) {
            String request = null;
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
            String result = "Unknown function " + function;
            switch (function){
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

            try {
                sendData(result);
            } catch (IOException e) {
                log("Unable to send result to client!");
            }
        }
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

    private String receiveData() throws IOException {
        String encryptedData = inStream.readUTF();
        return AESUtil.decrypt(encryptedData, secretKey);
    }

    private void sendData(String data) throws IOException {
        String encryptedData = AESUtil.encrypt(data, secretKey);
        outStream.writeUTF(encryptedData);
        outStream.flush();
    }

    private void log(String msg) {
        System.out.println(handle.getRemoteSocketAddress().toString() + "\t|\t" + msg);
    }

    private String search(String args){
        String res = "An unexpected error occurred!!";

        try {
            String sql = "SELECT * from users WHERE username='" + args +"' or fullName='" + args + "'";
            ResultSet dbRes = dbQuery.executeQuery(sql);

            res = "Names -\n";
            while (dbRes.next()) {
                String name = dbRes.getString("fullName");
                int id = dbRes.getInt("id");
                res += id + " : " + name +"\n";
            }

            dbRes.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return res;
    }

    private String send(String args){
        return "Send not implemented";
    }
}
