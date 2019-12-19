package server;

import java.io.*;
import java.net.*;

public class Server {
    /**
     * Main server process that accepts requests and diverts them to corresponding threads
     */
    public static void main(String[] args){
        ServerSocket server;
        System.out.println("Starting chat server...");
        try {
            server = new ServerSocket(7200);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        System.out.println("server open on port : 7200");
        while (true) {
            Socket sock;
            try {
                sock = server.accept();
            } catch (IOException e) {
                System.out.println("Error accepting requests!");
                continue;
            }
            System.out.println("Connection accepted! Starting client thread...");
            ChatClientHandler handler = new ChatClientHandler(sock);
            Thread handleThread = new Thread(handler);
            handleThread.start();
            System.out.println("Client Handler Process started");
        }
    }
}
