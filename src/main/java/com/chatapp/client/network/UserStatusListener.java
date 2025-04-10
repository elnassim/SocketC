package com.chatapp.client.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.function.Consumer;
import javafx.application.Platform;

public class UserStatusListener implements Runnable {
    private MulticastSocket socket;
    private final int port = 4446; // Port choisi pour la réception UDP
    private volatile boolean running = true;
    private Consumer<UserStatus> statusUpdateCallback;
    private InetAddress group;

    public UserStatusListener(Consumer<UserStatus> statusUpdateCallback) throws IOException {
        this.statusUpdateCallback = statusUpdateCallback;
        // Crée un MulticastSocket sur le port spécifié
        socket = new MulticastSocket(port);
        // Active la réutilisation d'adresse
        socket.setReuseAddress(true);
        // Utilise une adresse multicast (ex. 230.0.0.0)
        group = InetAddress.getByName("230.0.0.0");
        socket.joinGroup(group);
    }

    @Override
    public void run() {
        byte[] buf = new byte[256];
        while (running) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
                String received = new String(packet.getData(), 0, packet.getLength());
                String[] parts = received.split(":");
                if (parts.length == 2) {
                    String email = parts[0];
                    String status = parts[1];
                    Platform.runLater(() -> {
                        statusUpdateCallback.accept(new UserStatus(email, status));
                    });
                }
            } catch (IOException e) {
                if (running) {
                    e.printStackTrace();
                }
            }
        }
        socket.close();
    }

    public void stop() {
        running = false;
        socket.close();
    }
}