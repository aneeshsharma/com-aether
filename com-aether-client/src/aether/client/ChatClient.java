package aether.client;

import java.io.*;
import java.net.*;

public class ChatClient {
    public static void main(String[] args) {
        Socket sock;
        DataOutputStream outStream;
        DataInputStream inStream;

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        try{
            sock = new Socket("localhost", 7200);
            inStream = new DataInputStream(sock.getInputStream());
            outStream = new DataOutputStream(sock.getOutputStream());
        } catch (IOException e){
            System.out.println("Error connecting to server!!\n"+e);
            return;
        }

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
                outStream.writeUTF(input);
                outStream.flush();
                String str = inStream.readUTF();
                System.out.println("Message Received : " + str);
            }
            catch (IOException e){
                System.out.println("Error sending request!");
                break;
            }
        }

        try {
            outStream.close();
            inStream.close();
            sock.close();
        } catch (IOException e){
            System.out.println("Error closing connection!");
        }
    }
}
