package server;

import common.Message;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Channel {
    private String name;
    private Set<ClientHandler> members = ConcurrentHashMap.newKeySet();
    private List<Message> history = new ArrayList<>();

    public Channel(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void broadcast(Message msg) {
        history.add(msg);
        members.forEach(member -> member.sendMessage(msg));
    }

    public void addMember(ClientHandler client) {
        members.add(client);
        broadcast(
                new Message("System", client.getUsername() + " a rejoint #" + name, name, Message.MessageType.SYSTEM));
    }

    public void removeMember(ClientHandler client) {
        members.remove(client);
        broadcast(new Message("System", client.getUsername() + " a quitté #" + name, name, Message.MessageType.SYSTEM));
    }
}