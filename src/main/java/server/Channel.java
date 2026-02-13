package server;

import common.Message;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Channel {
    private String name;
    private String type; // TEXT or VOICE
    private String serverName;
    private Set<ClientHandler> members = ConcurrentHashMap.newKeySet();
    private List<Message> history = new ArrayList<>();

    public Channel(String name, String type, String serverName) {
        this.name = name;
        this.type = type;
        this.serverName = serverName;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getServerName() {
        return serverName;
    }

    public Set<ClientHandler> getMembers() {
        return members;
    }

    public void broadcast(Message msg) {
        // Sauvegarder uniquement les messages de chat
        if (msg.getType() == Message.MessageType.CHAT || msg.getType() == Message.MessageType.FILE) {
            DatabaseManager.saveMessage(msg);
        }
        history.add(msg); // Garder en mémoire pour session courante (optionnel maintenance)
        members.forEach(member -> member.sendMessage(msg));
    }

    public void addMember(ClientHandler client) {
        // Envoyer l'historique
        List<Message> savedHistory = DatabaseManager.getLastMessages(name, 50);
        for (Message m : savedHistory) {
            client.sendMessage(m);
        }

        members.add(client);
        // broadcast(new Message("System", client.getUsername() + " a rejoint #" + name,
        // name, Message.MessageType.SYSTEM));

        // Broadcast updated member list for this channel
        broadcastMemberList();
    }

    public void removeMember(ClientHandler client) {
        members.remove(client);
        // broadcast(new Message("System", client.getUsername() + " a quitté #" + name,
        // name, Message.MessageType.SYSTEM));
        broadcastMemberList();
    }

    private void broadcastMemberList() {
        StringBuilder sb = new StringBuilder();
        for (ClientHandler m : members) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(m.getUsername());
        }
        Message msg = new Message("System", sb.toString(), name, Message.MessageType.CHANNEL_USERS);
        members.forEach(member -> member.sendMessage(msg));
    }
}