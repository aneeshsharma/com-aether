package server;

import java.io.*;
import java.net.*;

public class DefaultHandler implements Runnable {
    private Socket handle;

    /**
     * Constructor for the DefaultHandler that handles simple requests using a test message
     * @param sock The socket to handle the requests for
     */
    public DefaultHandler(Socket sock){
        handle = sock;
    }

    public void run(){
        try {
            DataInputStream inStream = new DataInputStream(handle.getInputStream());
            DataOutputStream outStream = new DataOutputStream(handle.getOutputStream());
            String data = inStream.readUTF();
            System.out.println("Data received : " + data);
            outStream.writeUTF("Thank you for connecting to default handler!");
            outStream.flush();
            outStream.close();
            inStream.close();
            handle.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
