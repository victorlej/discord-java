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

            // Authentification
            output.writeObject(new Message("System", "Authentification requise", "auth", Message.MessageType.SYSTEM));
            output.flush();

            while (true) {
                try {
                    Message auth = (Message) input.readObject();
                    String candidatesUsername = auth.getUsername();
                    String content = auth.getContent(); // password:MODE

                    if (candidatesUsername == null || candidatesUsername.trim().isEmpty()) {
                        continue;
                    }

                    String password = "";
                    String mode = "LOGIN"; // default

                    if (content != null && content.contains(":")) {
                        String[] parts = content.split(":", 2);
                        password = parts[0];
                        if (parts.length > 1)
                            mode = parts[1];
                    } else if (content != null) {
                        password = content;
                    }

                    if ("REGISTER".equals(mode)) {
                        if (DatabaseManager.userExists(candidatesUsername)) {
                            output.writeObject(
                                    new Message("System", "Ce pseudo est déjà utilisé. Essayez de vous connecter.",
                                            "auth", Message.MessageType.SYSTEM));
                            output.flush();
                            continue;
                        }
                        DatabaseManager.registerUser(candidatesUsername, password);
                    } else {
                        // LOGIN
                        if (!DatabaseManager.userExists(candidatesUsername)) {
                            // Optionally auto-register if we want lazy-registration?
                            // User asked for account creation system, so we should be strict.
                            output.writeObject(new Message("System", "Compte inexistant. Veuillez vous inscrire.",
                                    "auth", Message.MessageType.SYSTEM));
                            output.flush();
                            continue;
                        }
                        if (!DatabaseManager.authenticateUser(candidatesUsername, password)) {
                            output.writeObject(new Message("System", "Mot de passe incorrect.", "auth",
                                    Message.MessageType.SYSTEM));
                            output.flush();
                            continue;
                        }
                    }

                    if (Server.clients.containsKey(candidatesUsername)) {
                        output.writeObject(new Message("System", "Utilisateur déjà connecté.", "auth",
                                Message.MessageType.SYSTEM));
                        output.flush();
                        continue;
                    }

                    this.username = candidatesUsername;
                    output.writeObject(new Message("System", "Authentification réussie", "auth_success",
                            Message.MessageType.SYSTEM));
                    output.flush();
                    break;

                } catch (Exception e) {
                    System.out.println("Erreur d'authentification: " + e.getMessage());
                    return;
                }
            }

            Server.registerClient(username, this);

            // Vérifier/Accorder droits si localhost
            InetAddress addr = socket.getInetAddress();
            if (addr.isLoopbackAddress() || addr.getHostAddress().equals("127.0.0.1")
                    || addr.getHostAddress().equals("0:0:0:0:0:0:0:1")) {
                DatabaseManager.setCanCreateChannel(username, true);
                System.out.println("Droits admin accordés automatiquement à " + username + " (Localhost)");
            }

            // Vérifier si bloqué
            if (DatabaseManager.isBlocked(username)) {
                output.writeObject(new Message("System", "Vous êtes bloqué.", "auth", Message.MessageType.SYSTEM));
                disconnect();
                return;
            }

            System.out.println("Client enregistré: " + username);

            // Rejoindre le général par défaut
            joinChannel("general");

            // Envoyer la liste des salons
            Server.broadcastChannelList();

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
        } else if (content.startsWith("/create ")) {
            String[] parts = content.split(" ");
            if (parts.length >= 2) {
                String channelName = parts[1].trim();
                String type = parts.length > 2 ? parts[2].toUpperCase() : "TEXT";

                if (DatabaseManager.canCreateChannel(this.username)) {
                    Server.createChannel(channelName, type);
                    sendMessage(new Message("System", "Salon #" + channelName + " (" + type + ") créé.", "system",
                            Message.MessageType.SYSTEM));
                } else {
                    sendMessage(new Message("System", "Vous n'avez pas la permission de créer des salons.", "system",
                            Message.MessageType.SYSTEM));
                }
            }
        } else if (content.startsWith("/deletechannel ")) {
            String channelName = content.substring(15).trim();
            if (DatabaseManager.canCreateChannel(this.username)) {
                Server.deleteChannel(channelName);
                sendMessage(new Message("System", "Salon #" + channelName + " supprimé.", "system",
                        Message.MessageType.SYSTEM));
            }
        } else if (content.startsWith("/renamechannel ")) {
            String[] parts = content.split(" ");
            if (parts.length >= 3) {
                String oldName = parts[1];
                String newName = parts[2];
                if (DatabaseManager.canCreateChannel(this.username)) {
                    Server.renameChannel(oldName, newName);
                    sendMessage(new Message("System", "Salon #" + oldName + " renommé en #" + newName, "system",
                            Message.MessageType.SYSTEM));
                }
            }
        } else if (content.startsWith("/grant ")) {
            String target = content.substring(7).trim();
            DatabaseManager.setCanCreateChannel(target, true);
            sendMessage(new Message("System", "Droit de création accordé à " + target, "system",
                    Message.MessageType.SYSTEM));
        } else if (content.startsWith("/block ")) {
            String target = content.substring(7).trim();
            DatabaseManager.blockUser(target, true);
            ClientHandler targetClient = Server.clients.get(target);
            if (targetClient != null)
                targetClient.disconnect();
            sendMessage(new Message("System", target + " a été bloqué.", "system", Message.MessageType.SYSTEM));
        } else if (content.startsWith("/kick ")) {
            String target = content.substring(6).trim();
            DatabaseManager.deleteUser(target);
            ClientHandler targetClient = Server.clients.get(target);
            if (targetClient != null)
                targetClient.disconnect();
            sendMessage(new Message("System", target + " a été supprimé.", "system", Message.MessageType.SYSTEM));
        } else {
            // Message normal ou Fichier dans le canal actuel
            if (currentChannel != null) {
                // Si c'est un fichier, on diffuse le message tel quel (avec les bytes)
                if (msg.getType() == Message.MessageType.FILE) {
                    currentChannel.broadcast(msg);
                } else {
                    // Sinon c'est un CHAT, on peut le reconstruire pour être sûr ou juste passer
                    currentChannel.broadcast(
                            new Message(username, content, currentChannel.getName(), Message.MessageType.CHAT));
                }
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