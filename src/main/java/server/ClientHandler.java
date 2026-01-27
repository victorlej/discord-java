package server;

import common.Message;
import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private String username;
    private Channel currentChannel;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            output = new ObjectOutputStream(socket.getOutputStream());
            // Flush header immediately to avoid blocking on client side input creation
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());

            // Authentification simple
            output.writeObject(new Message("System", "Entrez votre pseudo:", "auth", Message.MessageType.SYSTEM));
            output.flush();

            try {
                Message auth = (Message) input.readObject();
                // Simple validation to ensure unique username
                String candidatesUsername = auth.getUsername();
                while (Server.clients.containsKey(candidatesUsername)) {
                    output.writeObject(new Message("System", "Pseudo pris, choisissez-en un autre:", "auth",
                            Message.MessageType.SYSTEM));
                    output.flush();
                    auth = (Message) input.readObject();
                    candidatesUsername = auth.getUsername();
                }
                this.username = candidatesUsername;
            } catch (Exception e) {
                System.out.println("Erreur d'authentification: " + e.getMessage());
                return;
            }

            Server.registerClient(username, this);
            System.out.println("Client enregistré: " + username);

            // Rejoindre le général par défaut
            joinChannel("general");

            // Boucle de réception
            while (true) {
                Message msg = (Message) input.readObject();
                handleCommand(msg);
            }
        } catch (EOFException e) {
            // Client disconnected gracefully-ish
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Communication error with " + username + ": " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleCommand(Message msg) {
        String content = msg.getContent();

        if (content.startsWith("/join ")) {
            String channelName = content.substring(6).trim();
            if (!channelName.isEmpty())
                joinChannel(channelName);
        } else if (content.startsWith("/msg ")) {
            // Message privé: /msg username message
            String[] parts = content.split(" ", 3);
            if (parts.length >= 3) {
                sendPrivateMessage(parts[1], parts[2]);
            }
        } else if (content.startsWith("/list")) {
            listChannels();
        } else {
            // Message normal dans le canal actuel
            if (currentChannel != null) {
                currentChannel
                        .broadcast(new Message(username, content, currentChannel.getName(), Message.MessageType.CHAT));
            }
        }
    }

    private void joinChannel(String channelName) {
        if (currentChannel != null) {
            currentChannel.removeMember(this);
        }
        currentChannel = Server.getChannel(channelName);
        currentChannel.addMember(this);

        // Envoyer l'historique au nouveau membre (Optional TODO)
        sendMessage(new Message("System", "Vous êtes maintenant dans #" + channelName, channelName,
                Message.MessageType.SYSTEM));
    }

    public void sendMessage(Message msg) {
        try {
            output.writeObject(msg);
            output.flush();
        } catch (IOException e) {
            // If sending fails, we might just assume disconnected or wait for the read loop
            // to catch it
        }
    }

    private void sendPrivateMessage(String targetUser, String content) {
        ClientHandler target = Server.clients.get(targetUser);
        if (target != null) {
            Message privMsg = new Message(username, content, "PM", Message.MessageType.PRIVATE);
            target.sendMessage(privMsg);
            sendMessage(privMsg); // Echo to sender
        } else {
            sendMessage(
                    new Message("System", "Utilisateur non trouvé: " + targetUser, "PM", Message.MessageType.SYSTEM));
        }
    }

    private void listChannels() {
        StringBuilder sb = new StringBuilder("Salons disponibles:\n");
        Server.getAllChannels().forEach(ch -> sb.append("#").append(ch.getName()).append("\n"));
        sendMessage(new Message("System", sb.toString(), "system", Message.MessageType.SYSTEM));
    }

    private void disconnect() {
        if (username != null) {
            if (currentChannel != null)
                currentChannel.removeMember(this);
            Server.removeClient(username);
            System.out.println(username + " déconnecté");
        }
        try {
            socket.close();
        } catch (IOException e) {
        }
    }

    public String getUsername() {
        return username;
    }
}