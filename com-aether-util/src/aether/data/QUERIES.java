package aether.data;

public class QUERIES {
    public static final String USERS_TABLE_CREATE_QUERY = "CREATE TABLE users" +
            "(id INT PRIMARY KEY AUTO_INCREMENT," +
            "username varchar(50) NOT NULL," +
            "dateJoined DATE," +
            "fullName VARCHAR(100)," +
            "user_key VARCHAR(32)," +
            "public_key VARCHAR(216))";

    public static final String CHATS_TABLE_CREATE_QUERY = "CREATE TABLE chats " +
            "(id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "receiver_name VARCHAR(50) NOT NULL UNIQUE," +
            "table_name VARCHAR(50) NOT NULL UNIQUE," +
            "last_message TEXT," +
            "last_message_date DATETIME," +
            "type VARCHAR(10)," +
            "private_key VARCHAR())";

    public static final String NEW_UPDATE_TABLE_FIELDS = "(id INT PRIMARY KEY, from VARCHAR(50) NOT NULL, type VARCHAR(10) NOT NULL, update_data TEXT NOT NULL, date_created DATETIME)";
    public static final String NEW_CHAT_TABLE_FIELDS = "(id INTEGER PRIMARY KEY AUTOINCREMENT, author VARCHAR(50), message TEXT, message_date DATETIME, message_status VARCHAR(10))";
}
