package com.chatapp.server.handler;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.chatapp.server.service.UserService;
import com.chatapp.server.service.GroupService;
import com.chatapp.server.service.MessageService;
import com.chatapp.common.model.Group;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private List<ClientHandler> clients;
    private String userEmail;
    private UserService userService;
    private MessageService messageService;
    
    // service singleton pour les groupes
    private static final GroupService groupService = new GroupService();

    public ClientHandler(Socket socket, List<ClientHandler> clients) throws IOException {
        this.clientSocket   = socket;
        this.clients        = clients;
        this.in             = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out            = new PrintWriter(socket.getOutputStream(), true);
        this.userService    = new UserService();
        this.messageService = new MessageService();
    }

    @Override
    public void run() {
        try {
            String credentials = in.readLine();
            JSONObject loginRequest = new JSONObject(credentials);
            String email    = loginRequest.getString("email");
            String password = loginRequest.getString("password");
            this.userEmail = email;

            if (userService.authenticateUser(email, password)) {
                out.println("AUTH_SUCCESS");
                handleChat();
            } else {
                out.println("AUTH_FAILED");
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void cleanup() {
        clients.remove(this);
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleChat() throws IOException {
        sendOfflineMessages();
        String input;
        while ((input = in.readLine()) != null) {
            try {
                JSONObject messageJson = new JSONObject(input);
                String type = messageJson.getString("type");
                switch (type) {
                    case "GET_HISTORY":
                        handleHistoryRequest(messageJson);
                        break;
                    case "private":
                        handlePrivateMessage(messageJson);
                        break;
                    case "broadcast":
                        handleBroadcastMessage(messageJson);
                        break;
                    case "read_receipt":
                        handleReadReceipt(messageJson);
                        break;
                    case "create_group":
                        handleCreateGroup(messageJson);
                        break;
                    case "get_groups":
                        handleGetGroups();
                        break;
                    case "disconnect":
                        return;
                    case "audio":                              // ← nouvelle prise en charge
                        handleAudioMessage(messageJson);
                        break;
                    default:
                        broadcastMessage(userEmail + ": " + input);
                }
            } catch (JSONException e) {
                broadcastMessage(userEmail + ": " + input);
            }
        }
    }

    private void handlePrivateMessage(JSONObject messageJson) throws JSONException {
        String recipient = messageJson.getString("to");
        String content   = messageJson.getString("content");
        if (groupService.findGroupByName(recipient) != null) {
            handleGroupMessage(recipient, content);
        } else {
            handleDirectMessage(recipient, content);
        }
    }

    private void handleDirectMessage(String recipient, String content) throws JSONException {
        JSONObject routingMessage = messageService.createPrivateMessage(userEmail, recipient, content);
        String messageId = routingMessage.getString("id");
        ClientHandler dest = findClientByEmail(recipient);
        if (dest != null) {
            dest.sendMessage(routingMessage.toString());
            sendDeliveryReceipt(messageId, "delivered");
        } else {
            messageService.storeOfflineMessage(recipient, routingMessage);
            sendDeliveryReceipt(messageId, "pending");
        }
    }

    private void handleGroupMessage(String groupName, String content) throws JSONException {
        Group group = groupService.findGroupByName(groupName);
        if (group == null) {
            JSONObject err = new JSONObject();
            err.put("type", "error");
            err.put("content", "Group not found: " + groupName);
            sendMessage(err.toString());
            return;
        }
        List<String> members = group.getMembersEmails();
        String messageId = "msg_" + System.currentTimeMillis() + "_" + Integer.toHexString((int)(Math.random()*10000));
        JSONObject routing = new JSONObject();
        routing.put("id", messageId);
        routing.put("type", "group");
        routing.put("sender", userEmail);
        routing.put("content", content);
        routing.put("groupName", groupName);
        for (String m : members) {
            ClientHandler h = findClientByEmail(m);
            if (h != null) h.sendMessage(routing.toString());
            else messageService.storeOfflineMessage(m, routing);
        }
    }

    private void handleBroadcastMessage(JSONObject messageJson) throws JSONException {
        String content = messageJson.getString("content");
        JSONObject b = messageService.createBroadcastMessage(userEmail, content);
        broadcastMessage(b.toString());
    }

    private void handleReadReceipt(JSONObject messageJson) throws JSONException {
        String messageId = messageJson.getString("messageId");
        String sender    = messageJson.getString("sender");
        ClientHandler h  = findClientByEmail(sender);
        if (h != null) {
            JSONObject r = new JSONObject();
            r.put("type", "read_receipt");
            r.put("messageId", messageId);
            r.put("reader", userEmail);
            r.put("timestamp", System.currentTimeMillis());
            h.sendMessage(r.toString());
        }
    }

    private void handleCreateGroup(JSONObject messageJson) throws JSONException {
        String groupName = messageJson.getString("groupName");
        JSONArray arr    = messageJson.getJSONArray("members");
        List<String> members = new ArrayList<>();
        for (int i=0; i<arr.length(); i++) members.add(arr.getString(i));
        if (!members.contains(userEmail)) members.add(userEmail);
        Group g = groupService.createGroup(groupName, members);
        if (g != null) {
            for (String m : members) {
                ClientHandler h = findClientByEmail(m);
                if (h != null) {
                    JSONObject notif = new JSONObject();
                    notif.put("type", "group_created");
                    notif.put("groupName", groupName);
                    notif.put("members", members);
                    h.sendMessage(notif.toString());
                }
            }
            JSONObject ack = new JSONObject();
            ack.put("type", "system");
            ack.put("content", "Group '" + groupName + "' created");
            sendMessage(ack.toString());
        } else {
            JSONObject err = new JSONObject();
            err.put("type", "error");
            err.put("content", "Failed to create group '" + groupName + "'");
            sendMessage(err.toString());
        }
    }

    private void handleHistoryRequest(JSONObject req) throws JSONException {
        String other = req.getString("otherUser");
        List<JSONObject> history = messageService.getMessageHistory(userEmail, other);
        JSONObject resp = new JSONObject();
        resp.put("type", "HISTORY_RESPONSE");
        JSONArray msgs = new JSONArray();
        for (JSONObject m : history) msgs.put(m);
        resp.put("messages", msgs);
        sendMessage(resp.toString());
    }

    private void sendOfflineMessages() {
        for (String msg : messageService.getOfflineMessages(userEmail)) {
            sendMessage(msg);
        }
    }

    private void sendDeliveryReceipt(String messageId, String status) throws JSONException {
        JSONObject r = new JSONObject();
        r.put("type", "delivery_receipt");
        r.put("messageId", messageId);
        r.put("status", status);
        sendMessage(r.toString());
    }

    private ClientHandler findClientByEmail(String email) {
        for (ClientHandler c : clients) {
            if (email.equals(c.userEmail)) return c;
        }
        return null;
    }

    private void broadcastMessage(String message) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                c.sendMessage(message);
            }
        }
    }

    public void sendMessage(String message) {
        if (out != null) out.println(message);
    }

    private void handleGetGroups() throws JSONException {
        List<Group> gs = groupService.getGroupsByUser(userEmail);
        JSONObject resp = new JSONObject();
        resp.put("type", "groups_list");
        JSONArray arr = new JSONArray();
        for (Group g : gs) {
            JSONObject o = new JSONObject();
            o.put("name", g.getGroupName());
            o.put("members", g.getMembersEmails());
            arr.put(o);
        }
        resp.put("groups", arr);
        sendMessage(resp.toString());
    }

    /** Nouveauté : gestion des messages audio **/
    private void handleAudioMessage(JSONObject msgJson) throws JSONException {
        String convId  = msgJson.getString("conversationId");
        String sender  = msgJson.getString("sender");
        String fname   = msgJson.getString("fileName");
        String b64     = msgJson.getString("audioData");

        try {
            byte[] audioBytes = Base64.getDecoder().decode(b64);
            String savedName  = UUID.randomUUID() + "_" + fname;
            Path path        = Paths.get("uploads", savedName);
            Files.createDirectories(path.getParent());
            Files.write(path, audioBytes);

            JSONObject outMsg = new JSONObject();
            outMsg.put("type", "audio");
            outMsg.put("conversationId", convId);
            outMsg.put("sender", sender);
            outMsg.put("fileName", savedName);
            outMsg.put("audioData", b64);

            // diffusion au destinataire ou au groupe
            if (groupService.findGroupByName(convId) != null) {
                Group grp = groupService.findGroupByName(convId);
                for (String m : grp.getMembersEmails()) {
                    ClientHandler h = findClientByEmail(m);
                    if (h != null) h.sendMessage(outMsg.toString());
                }
            } else {
                ClientHandler dest = findClientByEmail(convId);
                if (dest != null) dest.sendMessage(outMsg.toString());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
