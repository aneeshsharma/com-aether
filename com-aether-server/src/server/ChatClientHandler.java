package server;

import java.net.*;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

import aether.data.QUERIES;
import aether.exceptions.ConnectionError;
import aether.security.*;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.xml.transform.Result;

public class ChatClientHandler implements Runnable{
    private Socket handle;
    private String processId;

    private final String JDBC_DRIVER = "org.mariadb.jdbc.Driver";
    private final String DB_URL = "jdbc:mysql://localhost/com_aether_db";

    private final String USER = "com-aether";
    private final String PASS = "letschat";

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

        try {
            establishSecureConnection();
        } catch (ConnectionError connectionError) {
            System.out.println("Cannot establish secure connection : " + connectionError.getMessage());
            connectionError.printStackTrace();
            closeConnection();
            return;
        }

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
                case "connect":
                    result = connectToUser(args);
                    break;
                case "connection_request":
                    result = placeConnectionRequest(args);
                    break;
                case "update":
                    result = publishUpdates(args);
                    break;
                case "get_updates":
                    result = getUpdates(args);
                    break;
                case "nothing":
                    result = "OKAY";
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

    private void updateRequest(String username, String from, String update_data) throws SQLException {
        if (!tableExists(getTableName(username))) {
            createUpdatesTable(username);
        }
        String sql = "INSERT INTO " + getTableName(username) + " (from_user, type, update_data, date_created) VALUES ('" + from + "', 'UPDATE', '" + update_data + "', CURDATE())";
        dbQuery.executeUpdate(sql);
    }

    private void connectionRequest(String username, String from, String update_data) throws SQLException {
        if (!tableExists(getTableName(username))) {
            createUpdatesTable(username);
        }
        if (alreadyRequested(username, from))
            return;
        String sql = "INSERT INTO " + getTableName(username) + " (from_user, type, update_data, date_created) VALUES ('" + from + "', 'REQUEST', '" + update_data + "', CURDATE())";
        dbQuery.executeUpdate(sql);
    }

    private String getUpdates(String args) {
        String sql = "SELECT * FROM " + getTableName(args) + " ORDER BY id";
        ResultSet resultSet = null;
        try {
            resultSet = dbQuery.executeQuery(sql);
        } catch (SQLException e) {
            try {
                sendData("UNABLE TO FETCH UPDATES");
            } catch (IOException ex) {
                log("Unable to send data to client!");
            }
        }

        assert resultSet != null;
        String reply = "NEXT";
        boolean sendComplete = true;
        try {
            while (resultSet.next()) {
                String send;
                try {
                    send = "UPDATE:" + resultSet.getInt("id") + "," + resultSet.getString("from_user") + "," + resultSet.getString("type") + "," + resultSet.getString("update_data") + "," + resultSet.getString("date_created");
                } catch (SQLException e) {
                    try {
                        log ("SQL Exception : " + e.getMessage());
                        sendData("UNABLE TO FETCH UPDATES");
                    } catch (IOException ex) {
                        log("Unable to send data to client!");
                    }
                    sendComplete = false;
                    break;
                }

                try {
                    sendData(send);
                } catch (IOException e) {
                    log ("Unable to send data to client!");
                    sendComplete = false;
                    break;
                }
                try {
                    reply = receiveData();
                } catch (IOException e) {
                    log("Didn't receive reply from client!");
                    sendComplete = false;
                    break;
                }
                if (!reply.equals("NEXT")) {
                    sendComplete = false;
                    break;
                }
            }
        } catch (SQLException e) {
            try {
                sendData("UNABLE TO FETCH UPDATES");
            } catch (IOException ex) {
                log("Unable to send data to client!");
            }
        }
        if (sendComplete) {
            try {
                sendData("NO MORE UPDATES");
            } catch (IOException e) {
                log ("Unable to send data to client!");
            }
        } else {
            try {
                sendData("SEND LAST ID");
            } catch (IOException e) {
                log("Unable to send data to client!");
            }
        }

        String lastId = "-1";
        try {
            lastId = receiveData();
        } catch (IOException e) {
            log("Unable to receive last ID");
        }

        int deleteId = Integer.parseInt(lastId);
        String deleteQuery = "DELETE FROM " + getTableName(args) + " WHERE id<=" + deleteId;
        try {
            dbQuery.executeUpdate(deleteQuery);
        } catch (SQLException e) {
            return "UNABLE TO DELETE RECORDS";
        }
        return "DONE";
    }

    private boolean alreadyRequested(String username, String from) {
        String sql = "SELECT * FROM " + getTableName(username) + " WHERE from_user='" + from + "' AND type='REQUEST'";
        ResultSet resultSet = null;
        try {
            resultSet = dbQuery.executeQuery(sql);
            return getResultSize(resultSet) > 1;
        } catch (SQLException e) {
            return false;
        }
    }

    private String getTableName(String username) {
        return "update_data_" + username;
    }

    private void createUpdatesTable(String username) throws SQLException {
        String createQuery = "CREATE TABLE " + getTableName(username) + " " + QUERIES.NEW_UPDATE_TABLE_FIELDS;
        dbQuery.executeUpdate(createQuery);
    }

    private String receiveData() throws IOException {
        String encryptedData = inStream.readUTF();
        return AESUtil.decrypt(encryptedData, secretKey);
    }

    private void sendData(String data) throws IOException {
        String encryptedData = AESUtil.encrypt(data, secretKey);
        assert encryptedData != null;
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

    private void establishSecureConnection() throws ConnectionError {
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
            throw new ConnectionError("Client silent");
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

            if (!tableExists("users")) {
                log("Users table doesn't exist creating one");
                dbQuery.executeUpdate(QUERIES.USERS_TABLE_CREATE_QUERY);
            }
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean tableExists(String name) throws SQLException {
        DatabaseMetaData dbm = dbConn.getMetaData();
        ResultSet res = dbm.getTables(null, null, name, new String[] {"TABLE"});
        return res.next();
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

    private int getResultSize(ResultSet resultSet) throws SQLException {
        resultSet.last();
        int size = resultSet.getRow();
        resultSet.beforeFirst();
        return size;
    }

    private String login(String args) {
        String[] credentials = args.split(",");
        String username = credentials[0];
        String key = credentials[1];
        String sql = "SELECT * FROM users WHERE username='" + username + "'";
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

    private String userExists(String args) {
        String sql = "SELECT * FROM users WHERE username='" + args +"'";
        ResultSet dbRes;
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
        String publicKey = credentials[3];
        log("Login Key : " + key);
        try {
            String sql = "INSERT INTO users (username, dateJoined, fullName, user_key, public_key) VALUES ('" + username + "', CURDATE(), '" + fullName + "', '" + key + "', '" + publicKey + "')";
            dbQuery.executeQuery(sql);
            createUpdatesTable(username);
        } catch (SQLException e) {
            return "Failed | " + e.toString();
        }

        return "Success";
    }

    private String search(String args){
        StringBuilder res = new StringBuilder("An unexpected error occurred!!");

        try {
            String sql = "SELECT * FROM users WHERE username='" + args +"' or fullName='" + args + "'";
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

    private String connectToUser(String args) {
        if (userExists(args).equals("true")) {
            String sql = "SELECT public_key FROM users WHERE username='" + args + "'";
            ResultSet resultSet;
            try {
                resultSet = dbQuery.executeQuery(sql);
                if (resultSet.next()) {
                    return resultSet.getString("public_key");
                } else {
                    return "NO SUCH USER:" + args;
                }
            } catch (SQLException e) {
                return "SERVER ERROR";
            }
        } else {
            return "NO SUCH USER:" + args;
        }
    }

    private String placeConnectionRequest(String args) {
        String[] data = args.split(",");
        String from = user;
        String to = data[0];
        String requestData = data[1];
        log ("Placing connection request to " + to + "...");
        if (!userExists(to).equals("true"))
            return "USER DOESN'T EXIST";
        if (alreadyRequested(to, from))
            return "ALREADY REQUESTED";
        try {
            connectionRequest(to, from, requestData);
        } catch (SQLException e) {
            log("SQL Error : " + e);
            return "COULDN'T PLACE CONNECTION REQUEST";
        }

        log ("Placed connection request to " + to);
        return "CONNECTION REQUESTED";
    }

    private String publishUpdates(String args) {
        String[] data = args.split(",");
        String from = user;
        String to = data[0];
        String requestData = data[1];

        if (!userExists(to).equals("true"))
            return "USER DOESN'T EXIST";
        try {
            updateRequest(to, from, requestData);
        } catch (SQLException e) {
            log("SQL Error: " + e.getMessage());
            return "COULDN'T PLACE UPDATE REQUEST";
        }

        return "UPDATE SUCCESSFUL";
    }
}
