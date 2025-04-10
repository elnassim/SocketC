package com.chatapp.client.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UserStatusBroadcaster implements Runnable {
    private String email;
    private DatagramSocket socket;
    private InetAddress group;
    private final int port = 4446; // Le même port que pour l'écoute

    public UserStatusBroadcaster(String email) throws IOException {
         this.email = email;
         socket = new DatagramSocket();
         // Pour envoyer en multicast, indiquez la même adresse
         group = InetAddress.getByName("230.0.0.0");
    }

    public void broadcastStatus(String status) throws IOException {
        String message = email + ":" + status;
        byte[] buf = message.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, group, port);
        socket.send(packet);
    }
    

    @Override
    public void run() {
         try {
             broadcastStatus("online");
         } catch(IOException e) {
             e.printStackTrace();
         }
    }

    public void sendOffline() throws IOException {
        broadcastStatus("offline");
        socket.close();
    }
}
