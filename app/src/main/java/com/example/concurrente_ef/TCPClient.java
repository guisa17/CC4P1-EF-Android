package com.example.concurrente_ef;

import android.util.Log;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

public class TCPClient {

    private String serverMessage;
    private String serverIP;
    private int serverPort;
    private OnMessageReceived mMessageListener = null;
    private boolean mRun = false;
    private Socket socket;

    PrintWriter out;
    BufferedReader in;

    /**
     *  Constructor of the class. OnMessagedReceived listens for the messages received from server
     */
    public TCPClient(String serverIP, int serverPort, OnMessageReceived listener) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        mMessageListener = listener;
    }

    /**
     * Sends the message entered by client to the server
     * @param message text entered by client
     */
    public void sendMessage(String message) {
        if (out != null && !out.checkError()) {
            Log.d("TCP Client", "Sending message: " + message);
            out.println(message);
            out.flush();
        }
    }

    public void stopClient() {
        mRun = false;
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e("TCP", "Error closing socket", e);
            }
        }
    }

    public void connect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                mRun = true;
                try {
                    InetAddress serverAddr = InetAddress.getByName(serverIP);
                    Log.d("TCP Client", "Connecting...");

                    socket = new Socket(serverAddr, serverPort);
                    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    Log.d("TCP Client", "Connected.");
                    mMessageListener.messageReceived("Connected");

                    while (mRun) {
                        serverMessage = in.readLine();
                        if (serverMessage != null && mMessageListener != null) {
                            mMessageListener.messageReceived(serverMessage);
                        }
                        serverMessage = null;
                    }
                } catch (Exception e) {
                    Log.e("TCP", "Error", e);
                } finally {
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        Log.e("TCP", "Error closing socket", e);
                    }
                }
            }
        }).start();
    }

    // Method to check if the client is connected
    public boolean isConnected() {
        return socket != null && !socket.isClosed();
    }

    // Method to get input stream for reading responses
    public BufferedReader getInput() {
        return in;
    }

    //Declare the interface. The method messageReceived(String message) must be implemented in MainActivity
    public interface OnMessageReceived {
        void messageReceived(String message);
    }
}
