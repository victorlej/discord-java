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
            channels.put(cd.name, new Channel(cd.name, cd.type, cd.serverName));
        }

        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(PORT));

            System.out.println("🚀 Serveur Discord-like démarré sur le port " + PORT);

            // Start Voice UDP Server
            new Thread(new VoiceServer()).start();

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
        return channels.computeIfAbsent(name, n -> new Channel(n, "TEXT", "Main Server")); // Default fallback
    }

    public static void createChannel(String name, String type, String serverName) {
        if (!channels.containsKey(name)) {
            DatabaseManager.createChannel(name, type, serverName);
            channels.put(name, new Channel(name, type, serverName));
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
            String serverName = ch.getServerName();
            DatabaseManager.renameChannel(oldName, newName);
            channels.put(newName, new Channel(newName, type, serverName));
            broadcastChannelList();
        }
    }

    public static void broadcastChannelList() {
        StringBuilder sb = new StringBuilder();
        for (Channel ch : channels.values()) {
            if (sb.length() > 0)
                sb.append(",");
            sb.append(ch.getName()).append(":").append(ch.getType()).append(":").append(ch.getServerName());
        }
        Message msg = new Message("System", sb.toString(), "global", Message.MessageType.CHANNEL_LIST);
        for (ClientHandler client : clients.values()) {
            client.sendMessage(msg);
        }
    }

    public static void createServer(String name) {
        if (!DatabaseManager.serverExists(name)) {
            DatabaseManager.createServer(name);
            broadcastServerList();
        }
    }

    public static void deleteServer(String name) {
        if (!name.equals("Main Server") && DatabaseManager.serverExists(name)) {
            // Remove associated channels from memory
            Iterator<Map.Entry<String, Channel>> it = channels.entrySet().iterator();
            while (it.hasNext()) {
                if (name.equals(it.next().getValue().getServerName())) {
                    it.remove();
                }
            }
            // Delete from DB
            DatabaseManager.deleteServer(name);
            broadcastServerList();
            broadcastChannelList();
        }
    }

    public static void broadcastServerList() {
        String serverListString = String.join(",", DatabaseManager.getServers());
        Message msg = new Message("System", serverListString, "global", Message.MessageType.SERVER_LIST);

        for (ClientHandler client : clients.values()) {
            client.sendMessage(msg);
        }
    }

    public static Collection<Channel> getAllChannels() {
        return channels.values();
    }
}