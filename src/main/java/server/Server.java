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
            channels.put(cd.serverName + ":" + cd.name, new Channel(cd.name, cd.type, cd.serverName));
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

        // Notify friends that this user came online
        java.util.List<String> friends = DatabaseManager.getFriends(username);
        for (String friendName : friends) {
            ClientHandler friendHandler = clients.get(friendName);
            if (friendHandler != null) {
                java.util.List<String> theirFriends = DatabaseManager.getFriends(friendName);
                StringBuilder sb = new StringBuilder();
                for (String f : theirFriends) {
                    boolean online = clients.containsKey(f);
                    if (sb.length() > 0)
                        sb.append(",");
                    sb.append(f).append(":").append(online ? "Online" : "Offline");
                }
                friendHandler
                        .sendMessage(new Message("System", sb.toString(), "friends", Message.MessageType.FRIEND_LIST));
            }
        }
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
        // Handle composite key or legacy key
        if (channels.containsKey(name))
            return channels.get(name);

        // Return default if not found (legacy behavior fallback)
        return channels.computeIfAbsent(name, n -> new Channel(n, "TEXT", "Main Server"));
    }

    public static Channel getChannel(String name, String serverName) {
        return channels.get(serverName + ":" + name);
    }

    public static void createChannel(String name, String type, String serverName) {
        String key = serverName + ":" + name;
        if (!channels.containsKey(key)) {
            DatabaseManager.createChannel(name, type, serverName);
            channels.put(key, new Channel(name, type, serverName));
            broadcastChannelList();
        }
    }

    public static void deleteChannel(String name) {
        // Warn: This legacy method deletes channel "name" from ALL servers if we don't
        // know the server?
        // Or we should update signature. For now, try to find key ending with :name?
        // No, client must provide server context.
        // Let's assume name passed here is composite or we iterate.
        // Actually ClientHandler passes just name. This is dangerous with multiple
        // servers.
        // We need to upgrade deleteChannel to accept serverName.
    }

    public static void deleteChannel(String name, String serverName) {
        String key = serverName + ":" + name;
        if (channels.containsKey(key)) {
            DatabaseManager.deleteChannel(name, serverName); // Updated DB method needed
            channels.remove(key);
            broadcastChannelList();
        }
    }

    public static void renameChannel(String oldName, String newName) {
        // Legacy issue again. Assuming Main Server?
        // We will overload.
    }

    public static void renameChannel(String oldName, String newName, String serverName) {
        String oldKey = serverName + ":" + oldName;
        String newKey = serverName + ":" + newName;

        if (channels.containsKey(oldKey) && !channels.containsKey(newKey)) {
            Channel ch = channels.remove(oldKey);
            String type = ch.getType();
            DatabaseManager.renameChannel(oldName, newName, serverName); // Update DB method
            channels.put(newKey, new Channel(newName, type, serverName));
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
                Map.Entry<String, Channel> entry = it.next();
                if (name.equals(entry.getValue().getServerName())) {
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