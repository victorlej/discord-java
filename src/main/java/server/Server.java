package server;

import common.Message;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 5000;
    public static Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static Map<String, Channel> channels = new ConcurrentHashMap<>();
    private static ExecutorService pool = Executors.newFixedThreadPool(50);

    public static void main(String[] args) {
        // Initialisation BDD
        DatabaseManager.init();

        // Chargement des salons depuis la BDD
        for (DatabaseManager.ChannelData cd : DatabaseManager.getChannels()) {
            channels.put(cd.name, new Channel(cd.name, cd.type));
        }

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(PORT));

            System.out.println("🚀 Serveur Discord-like démarré sur le port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nouveau client connecté: " + clientSocket.getInetAddress());
                ClientHandler clientThread = new ClientHandler(clientSocket);
                pool.execute(clientThread);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void registerClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        broadcastUserList();
    }

    public static void removeClient(String username) {
        if (username != null) {
            clients.remove(username);
            broadcastUserList();
        }
    }

    public static void broadcastUserList() {
        String userListString = String.join(",", clients.keySet());
        Message msg = new Message("System", userListString, "global", Message.MessageType.USER_LIST);

        for (ClientHandler client : clients.values()) {
            client.sendMessage(msg);
        }
    }

    public static Channel getChannel(String name) {
        return channels.computeIfAbsent(name, n -> new Channel(n, "TEXT")); // Default fallback
    }

    public static void createChannel(String name, String type) {
        if (!channels.containsKey(name)) {
            DatabaseManager.createChannel(name, type);
            channels.put(name, new Channel(name, type));
            broadcastChannelList();
        }
    }

    public static void deleteChannel(String name) {
        if (channels.containsKey(name)) {
            DatabaseManager.deleteChannel(name);
            channels.remove(name);
            broadcastChannelList();
        }
    }

    public static void renameChannel(String oldName, String newName) {
        if (channels.containsKey(oldName) && !channels.containsKey(newName)) {
            Channel ch = channels.remove(oldName);
            String type = ch.getType();
            DatabaseManager.renameChannel(oldName, newName);
            channels.put(newName, new Channel(newName, type));
            broadcastChannelList();
        }
    }

    public static void broadcastChannelList() {
        StringBuilder sb = new StringBuilder();
        for (Channel ch : channels.values()) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(ch.getName()).append(":").append(ch.getType());
        }
        Message msg = new Message("System", sb.toString(), "global", Message.MessageType.CHANNEL_LIST);
        for (ClientHandler client : clients.values()) {
            client.sendMessage(msg);
        }
    }

    public static Collection<Channel> getAllChannels() {
        return channels.values();
    }
}