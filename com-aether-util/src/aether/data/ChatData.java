package aether.data;

import java.io.File;
import java.security.KeyException;
import java.sql.*;
import aether.data.QUERIES;
import aether.exceptions.FileDecryptionError;
import aether.exceptions.FileEncryptionError;
import aether.security.AESUtil;

public class ChatData {
    private final String JDBC_DRIVER = "org.sqlite.JDBC";
    private final String DB_URL_PREFIX = "jdbc:sqlite:";

    private Connection conn;
    private Statement stmt;
    private DatabaseMetaData dbm;
    private String processName;
    private String fileName;

    private String key;

    private boolean log;

    public ChatData(String processName, String fileName, String password) throws ClassNotFoundException, SQLException, KeyException, FileDecryptionError {
        String DB_URL = DB_URL_PREFIX + fileName;
        this.processName = processName;
        this.fileName = fileName;
        log = true;
        key = password;
        if (key.length() != 16) {
            throw new KeyException("Invalid key size " + key.length());
        }
        File dbFile = new File(fileName);
        File encFile = new File(fileName + ".enc");
        if (dbFile.exists() && !dbFile.isDirectory()) {
            log("Previous logout wasn't successful! Chat data was vulnerable");
        } else {
            if (encFile.exists() && !encFile.isDirectory()) {
                AESUtil.decryptFile(fileName + ".enc", fileName, key);
            } else {
                log("Creating new database");
            }
        }

        conn = null;
        stmt = null;
        dbm = null;

        Class.forName(JDBC_DRIVER);
        conn = DriverManager.getConnection(DB_URL);
        stmt = conn.createStatement();
        log("Opened database!");

        // Check if the chats table exists
        dbm = conn.getMetaData();
        if (!tableExists("chats")) {
            // Chats doesn't exist
            // Assume new database is to be created
            log("Executing : " + QUERIES.CHATS_TABLE_CREATE_QUERY);
            stmt.executeUpdate(QUERIES.CHATS_TABLE_CREATE_QUERY);
        }
    }

    private String getTableName(String receiverName) {
        return "chat_data_" + receiverName;
    }

    private String getCreateQuery(String receiverName) {
        return "CREATE TABLE " + getTableName(receiverName) + " " + QUERIES.NEW_CHAT_TABLE_FIELDS;
    }

    private boolean tableExists(String name) throws SQLException {
        ResultSet res = dbm.getTables(null, null, name, new String[] {"TABLE"});
        return res.next();
    }

    public void newChat(String receiverName) throws SQLException {
        if (!tableExists(getTableName(receiverName))) {
            String createQuery = getCreateQuery(receiverName);
            log("Executing : " + createQuery);
            stmt.executeUpdate(createQuery);
            String updateChatsQuery = "INSERT INTO chats (receiver_name, table_name, type) VALUES ('" + receiverName + "', '" + getTableName(receiverName) + "', 'PERSONAL')";
            stmt.executeUpdate(updateChatsQuery);
        }
    }

    public void addMessage(String receiverName, String message, String author, String status) throws SQLException {
        if (!tableExists(getTableName(receiverName))) {
            log("No such chat exists, creating new chat...");
            newChat(receiverName);
        }
        String sendQuery = "INSERT INTO " + getTableName(receiverName) + " (author, message, message_date, message_status) VALUES " +
                            "('" + author + "', '" + message + "', datetime('now'), '" + status + "')";
        log("Executing : " + sendQuery);
        stmt.executeUpdate(sendQuery);

        String updateChatsQuery = "UPDATE chats SET last_message='" + message + "', last_message_date=datetime('now') WHERE receiver_name='" + receiverName + "'";
        stmt.executeUpdate(updateChatsQuery);
    }

    public String getAllChats() throws SQLException {
        String getQuery = "SELECT * FROM chats";

        ResultSet resultSet = stmt.executeQuery(getQuery);
        StringBuilder result = new StringBuilder();
        while (resultSet.next()) {
            int id = resultSet.getInt("id");
            String receiverName = resultSet.getString("receiver_name");
            String lastMessage = resultSet.getString("last_message");
            String lastMessageDate = resultSet.getString("last_message_date");
            result.append(id).append(" | ").append(receiverName).append(" | ").append(lastMessage).append(" | ").append(lastMessageDate).append("\n");
        }

        return result.toString();
    }

    public String getLastNMessages(String receiverName, int n) throws SQLException {
        if (tableExists(getTableName(receiverName))) {
            String getQuery = "SELECT * FROM ( SELECT id, author, message, message_date, message_status, ROW_NUMBER() OVER (ORDER BY id DESC) AS rank FROM " + getTableName(receiverName) + ") " +
                    "WHERE rank <= " + n + " ORDER BY rank DESC";
            log("Executing : " + getQuery);
            ResultSet resultSet = stmt.executeQuery(getQuery);
            StringBuilder result = new StringBuilder();
            while (resultSet.next()) {
                int id = resultSet.getInt("id");
                String author = resultSet.getString("author");
                String message = resultSet.getString("message");
                String message_date = resultSet.getString("message_date");
                String status = resultSet.getString("message_status");
                result.append(id).append(" | ").append(author).append(" : ").append(message).append(" > ").append(message_date).append(" | ").append(status).append("\n");
            }
            return result.toString();
        } else {
            return "No such receiver";

        }
    }

    public void setLog(boolean doLog) {
        log = doLog;
    }

    private void log(String msg) {
        if (log)
            System.out.println(processName + " | " + msg);
    }

    public void close() throws SQLException, FileEncryptionError {
        AESUtil.encryptFile(fileName, fileName + ".enc", key);
        conn.close();
        stmt.close();
    }
}
