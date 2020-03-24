package aether.data;

public class QUERIES {
    public static final String CHATS_TABLE_CREATE_QUERY = "CREATE TABLE chats " +
            "(id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "receiver_name VARCHAR(50) NOT NULL UNIQUE," +
            "table_name VARCHAR(50) NOT NULL UNIQUE," +
            "last_message TEXT," +
            "last_message_date DATETIME," +
            "type VARCHAR(10))";

    public static final String NEW_CHAT_TABLE_FIELDS = "(id INTEGER PRIMARY KEY AUTOINCREMENT, author VARCHAR(50), message TEXT, message_date DATETIME, message_status VARCHAR(10))";
}
