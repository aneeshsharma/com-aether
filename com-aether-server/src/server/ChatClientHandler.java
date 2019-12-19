package server;

import java.net.*;
import java.io.*;
import java.security.spec.ECField;
import java.sql.*;
import java.util.*;

public class ChatClientHandler implements Runnable{
    private Socket handle;

    private final String JDBC_DRIVER = "org.mariadb.jdbc.Driver";
    private final String DB_URL = "jdbc:mysql://localhost/test";

    private final String USER = "joe";
    private final String PASS = "joemama";

    private Connection dbConn;
    private Statement dbQuery;

    public ChatClientHandler(Socket sock){
        handle = sock;
    }

    public void run(){
        DataInputStream inStream;
        DataOutputStream outStream;
        try {
            inStream = new DataInputStream(handle.getInputStream());
            outStream = new DataOutputStream(handle.getOutputStream());
        }
        catch (IOException e){
            System.out.println("Error occurred accepting the given request!\n" + e);
            try {
                handle.close();
            } catch (IOException ex) {
                System.out.println("Unable to close connection!");
            }
            return;
        }

        dbConn = null;
        dbQuery = null;

        try {
            Class.forName(JDBC_DRIVER);

            System.out.println("Connecting to database");
            dbConn = DriverManager.getConnection(DB_URL, USER, PASS);

            System.out.println("Creating a statement");
            dbQuery = dbConn.createStatement();
        } catch (Exception e) {
            e.printStackTrace();
        }

        while (true) {
            String request = null;
            try {
                request = inStream.readUTF();
            } catch (EOFException eof) {
                System.out.println("Client Disconnected!");
                break;
            } catch (IOException e) {
                System.out.println("An IO Exception Occurred!");
                break;
            }

            String function;
            String args;

            try {
                function = request.substring(0, request.indexOf(':'));
                args = request.substring(request.indexOf(':') + 1);
            } catch (Exception e){
                System.out.println("Invalid request format : " + request + "\n" + e);
                try {
                    outStream.writeUTF("Invalid Query!");
                    outStream.flush();
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
                    System.out.println("Unknown function " + function + " | Skipped");
                    result = "Invalid Query!!";
            }

            try {
                outStream.writeUTF(result);
                outStream.flush();
            } catch (IOException e) {
                System.out.println("Unable to send result to client!");
            }
        }
        try {
            inStream.close();
            outStream.close();
            handle.close();
        } catch (IOException e) {
            System.out.println("Unable to close socket and streams!");
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
