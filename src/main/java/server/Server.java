package server;

import common.Message;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Server {
    private static final int PORT = 5000;
    public static Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static Map<String, Channel> channels = new ConcurrentHashMap<>();
    private static ExecutorService pool = Executors.newFixedThreadPool(50);

    public static void main(String[] args) {
        // Création des salons par défaut
        channels.put("general", new Channel("general"));
        channels.put("dev", new Channel("dev"));
        channels.put("random", new Channel("random"));
        channels.put("gaming", new Channel("gaming")); // Added from Client UI

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
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
        return channels.computeIfAbsent(name, Channel::new);
    }

    public static Collection<Channel> getAllChannels() {
        return channels.values();
    }
}