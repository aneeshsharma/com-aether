package aether.client;

import java.io.*;
import java.net.*;

public class TestClient {
    public static void main(String args[]){
        try{
            Socket sock = new Socket("localhost", 7200);
            DataInputStream din = new DataInputStream(sock.getInputStream());
            DataOutputStream dout = new DataOutputStream(sock.getOutputStream());
            dout.writeUTF("Hello World!");
            dout.flush();
            String str = din.readUTF();
            System.out.println("Message Received : " + str);
            dout.close();
            din.close();
            sock.close();
        } catch (IOException e){
            System.out.println("Error connecting to server!!\n"+e);
        }
    }
}
