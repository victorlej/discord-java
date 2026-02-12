package server;

import common.Message;
import java.io.*;
import java.net.*;
import java.util.List;

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
                // DatabaseManager.setCanCreateChannel(username, true); // Legacy
                // Always ensure localhost has Admin role
                if (!DatabaseManager.hasPermission(username, "perm_manage_roles")) {
                    DatabaseManager.assignRole(username, "Admin");
                    System.out.println("Role Admin accordé automatiquement à " + username + " (Localhost)");
                }
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
            Server.broadcastServerList();

            // Boucle de réception
            while (true) {
                Message msg = (Message) input.readObject();
                if (msg.getType() == Message.MessageType.CREATE_SERVER) {
                    Server.createServer(msg.getContent());
                } else {
                    handleCommand(msg);
                }
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
        } else if (content.startsWith("/dm_history ")) {
            // Load DM history: /dm_history targetUser
            String targetUser = content.substring(12).trim();
            String dmChannel = getDMChannelName(this.username, targetUser);
            List<Message> history = DatabaseManager.getLastMessages(dmChannel, 50);
            for (Message hMsg : history) {
                sendMessage(hMsg);
            }
        } else if (content.startsWith("/call ")) {
            // Voice call request: /call targetUser
            String targetUser = content.substring(6).trim();
            ClientHandler target = Server.clients.get(targetUser);
            if (target != null) {
                // Send call notification to target
                target.sendMessage(
                        new Message(this.username, this.username, "call_request", Message.MessageType.SYSTEM));
                sendMessage(new Message("System", "Appel en cours vers " + targetUser + "...", "system",
                        Message.MessageType.SYSTEM));
            } else {
                sendMessage(new Message("System", targetUser + " n'est pas connecté.", "system",
                        Message.MessageType.SYSTEM));
            }
        } else if (content.startsWith("/list")) {
            listChannels();
        } else if (content.startsWith("/createrole ")) {
            // /createrole Name bitmask? Or args? Simplification: /createrole Name 1 1 1 1
            if (DatabaseManager.hasPermission(this.username, "perm_manage_roles")) {
                String[] parts = content.split(" ");
                if (parts.length >= 6) {
                    String rName = parts[1];
                    boolean pCreate = "1".equals(parts[2]) || "true".equalsIgnoreCase(parts[2]);
                    boolean pBlock = "1".equals(parts[3]) || "true".equalsIgnoreCase(parts[3]);
                    boolean pDel = "1".equals(parts[4]) || "true".equalsIgnoreCase(parts[4]);
                    boolean pManage = "1".equals(parts[5]) || "true".equalsIgnoreCase(parts[5]);

                    DatabaseManager.createRole(rName, pCreate, pBlock, pDel, pManage);
                    sendMessage(
                            new Message("System", "Rôle " + rName + " créé.", "system", Message.MessageType.SYSTEM));
                    sendRolesList(); // Update client
                } else {
                    sendMessage(
                            new Message("System", "Usage: /createrole <name> <pCreate> <pBlock> <pDel> <pManageRole>",
                                    "system", Message.MessageType.SYSTEM));
                }
            } else {
                sendMessage(new Message("System", "Permission refusée.", "system", Message.MessageType.SYSTEM));
            }
        } else if (content.startsWith("/assignrole ")) {
            if (DatabaseManager.hasPermission(this.username, "perm_manage_roles")) {
                String[] parts = content.split(" ");
                if (parts.length >= 3) {
                    DatabaseManager.assignRole(parts[1], parts[2]);
                    sendMessage(new Message("System", "Rôle " + parts[2] + " donné à " + parts[1], "system",
                            Message.MessageType.SYSTEM));
                }
            } else {
                sendMessage(new Message("System", "Permission refusée.", "system", Message.MessageType.SYSTEM));
            }
        } else if (content.startsWith("/create ")) {
            String[] parts = content.split(" ");
            if (parts.length >= 2) {
                String channelName = parts[1].trim();
                String type = parts.length > 2 ? parts[2].toUpperCase() : "TEXT";
                String server = "Main Server";
                if (parts.length > 3) {
                    server = String.join(" ", java.util.Arrays.copyOfRange(parts, 3, parts.length));
                }

                if (DatabaseManager.hasPermission(this.username, "perm_create_channel")) {
                    Server.createChannel(channelName, type, server);
                    sendMessage(new Message("System",
                            "Salon #" + channelName + " (" + type + ") créé dans " + server + ".", "system",
                            Message.MessageType.SYSTEM));
                }
            }
        } else if (content.startsWith("/deleteserver ")) {
            String srvName = content.substring(14).trim();
            InetAddress addr = socket.getInetAddress();
            boolean isLocalhost = addr.isLoopbackAddress() || addr.getHostAddress().equals("127.0.0.1")
                    || addr.getHostAddress().equals("0:0:0:0:0:0:0:1");

            if (isLocalhost) {
                if ("Main Server".equals(srvName)) {
                    sendMessage(new Message("System", "Impossible de supprimer le serveur principal.", "system",
                            Message.MessageType.SYSTEM));
                } else {
                    Server.deleteServer(srvName);
                    sendMessage(new Message("System", "Serveur '" + srvName + "' supprimé.", "system",
                            Message.MessageType.SYSTEM));
                }
            } else {
                sendMessage(
                        new Message("System", "Action réservée au localhost.", "system", Message.MessageType.SYSTEM));
            }
        } else if (content.startsWith("/deletechannel ")) {
            String channelName = content.substring(15).trim();
            if (DatabaseManager.hasPermission(this.username, "perm_create_channel")) {
                Server.deleteChannel(channelName);
                sendMessage(new Message("System", "Salon #" + channelName + " supprimé.", "system",
                        Message.MessageType.SYSTEM));
            } else {
                sendMessage(new Message("System", "Permission refusée.", "system", Message.MessageType.SYSTEM));
            }
        } else if (content.startsWith("/renamechannel ")) {
            String[] parts = content.split(" ");
            if (parts.length >= 3) {
                String oldName = parts[1];
                String newName = parts[2];
                if (DatabaseManager.hasPermission(this.username, "perm_create_channel")) {
                    Server.renameChannel(oldName, newName);
                    sendMessage(new Message("System", "Salon #" + oldName + " renommé en #" + newName, "system",
                            Message.MessageType.SYSTEM));
                }
            }
        } else if (content.startsWith("/grant ")) {
            if (DatabaseManager.hasPermission(this.username, "perm_manage_roles")) {
                // String target = content.substring(7).trim(); // Unused
                sendMessage(new Message("System", "Utilisez /assignrole pour gérer les permissions.", "system",
                        Message.MessageType.SYSTEM));
            }
        } else if (content.startsWith("/block ")) {
            if (DatabaseManager.hasPermission(this.username, "perm_block")) {
                String target = content.substring(7).trim();
                DatabaseManager.blockUser(target, true);
                ClientHandler targetClient = Server.clients.get(target);
                if (targetClient != null)
                    targetClient.disconnect();

                sendMessage(new Message("System", target + " a été bloqué.", "system", Message.MessageType.SYSTEM));
            } else

            {
                sendMessage(new Message("System", "Commande réservée aux modérateurs.", "system",
                        Message.MessageType.SYSTEM));
            }
        } else if (content.startsWith("/friend add ")) {
            String target = content.substring(12).trim();
            System.out.println("[DEBUG] /friend add called by " + this.username + " for target: " + target);
            // Expected format: pseudo#tag
            String[] parts = target.split("#");
            if (parts.length == 2) {
                String pseudo = parts[0];
                String tag = parts[1];

                System.out.println("[DEBUG] Parsed pseudo=" + pseudo + " tag=" + tag);

                if (pseudo.equals(this.username)) {
                    sendMessage(new Message("System", "Vous ne pouvez pas vous ajouter vous-même.", "system",
                            Message.MessageType.SYSTEM));
                } else if (!DatabaseManager.userExists(pseudo)) {
                    System.out.println("[DEBUG] User " + pseudo + " does NOT exist in database");
                    sendMessage(new Message("System", "L'utilisateur '" + pseudo + "' n'existe pas.", "system",
                            Message.MessageType.SYSTEM));
                } else {
                    String actualTag = DatabaseManager.getUserTag(pseudo);
                    System.out.println(
                            "[DEBUG] Actual tag for " + pseudo + " is: " + actualTag + " (provided: " + tag + ")");

                    if (actualTag != null && actualTag.equals(tag)) {
                        // Auto-accept: status=1 directement
                        DatabaseManager.addFriend(this.username, pseudo);
                        System.out.println(
                                "[DEBUG] addFriend called successfully for " + this.username + " -> " + pseudo);

                        sendMessage(new Message("System", "Vous êtes maintenant ami avec " + pseudo + " !", "system",
                                Message.MessageType.SYSTEM));

                        // Notify target if online
                        ClientHandler targetClient = Server.clients.get(pseudo);
                        if (targetClient != null) {
                            targetClient.sendMessage(new Message("System",
                                    this.username + " vous a ajouté en ami !", "system",
                                    Message.MessageType.SYSTEM));
                        }

                        // Refresh friend list
                        List<String> friends = DatabaseManager.getFriends(this.username);
                        System.out.println("[DEBUG] Friends list after add: " + friends);
                        StringBuilder sb = new StringBuilder();
                        for (String f : friends) {
                            boolean online = Server.clients.containsKey(f);
                            if (sb.length() > 0)
                                sb.append(",");
                            sb.append(f).append(":").append(online ? "Online" : "Offline");
                        }
                        sendMessage(new Message("System", sb.toString(), "friends", Message.MessageType.FRIEND_LIST));

                        // Also send updated friend list to the target user if online
                        if (targetClient != null) {
                            List<String> targetFriends = DatabaseManager.getFriends(pseudo);
                            StringBuilder sb2 = new StringBuilder();
                            for (String f : targetFriends) {
                                boolean onl = Server.clients.containsKey(f);
                                if (sb2.length() > 0)
                                    sb2.append(",");
                                sb2.append(f).append(":").append(onl ? "Online" : "Offline");
                            }
                            targetClient.sendMessage(
                                    new Message("System", sb2.toString(), "friends", Message.MessageType.FRIEND_LIST));
                        }
                    } else {
                        sendMessage(new Message("System",
                                "Tag incorrect pour " + pseudo + ". Vérifiez le tag.", "system",
                                Message.MessageType.SYSTEM));
                    }
                }
            } else {
                sendMessage(new Message("System", "Format requis: pseudo#tag (ex: Victor#1234)", "system",
                        Message.MessageType.SYSTEM));
            }
        } else if (content.startsWith("/friend list")) {
            List<String> friends = DatabaseManager.getFriends(this.username);
            StringBuilder sb = new StringBuilder();
            for (String f : friends) {
                boolean online = Server.clients.containsKey(f);
                if (sb.length() > 0)
                    sb.append(",");
                sb.append(f).append(":").append(online ? "Online" : "Offline");
            }
            sendMessage(new Message("System", sb.toString(), "friends", Message.MessageType.FRIEND_LIST));
        } else if (content.startsWith("/friend accept ")) {
            String target = content.substring(15).trim();
            DatabaseManager.acceptFriend(target, this.username);
            sendMessage(new Message("System", "Vous êtes maintenant ami avec " + target, "system",
                    Message.MessageType.SYSTEM));
        } else if (content.startsWith("/myid")) {
            String tag = DatabaseManager.getUserTag(this.username);
            // Send as USER_INFO so client can handle it specifically (e.g. update UI)
            sendMessage(new Message("System", this.username + "#" + tag, "user_info",
                    Message.MessageType.USER_INFO));
        } else if (content.startsWith("/kick ")) {
            if (DatabaseManager.hasPermission(this.username, "perm_block")) {
                String target = content.substring(6).trim();
                // DatabaseManager.deleteUser(target); // FIX: Do not delete user on kick
                ClientHandler targetClient = Server.clients.get(target);
                if (targetClient != null) {
                    targetClient.disconnect();
                    sendMessage(
                            new Message("System", target + " a été expulsé.", "system", Message.MessageType.SYSTEM));
                } else {
                    sendMessage(new Message("System", target + " n'est pas connecté.", "system",
                            Message.MessageType.SYSTEM));
                }
            } else {
                sendMessage(new Message("System", "Commande réservée aux modérateurs.", "system",
                        Message.MessageType.SYSTEM));
            }
        } else if (content.startsWith("/getroles")) {
            List<String> roles = DatabaseManager.getAllRoles();
            String rolesStr = String.join(",", roles);

            sendMessage(new Message("System", rolesStr, "ROLES_LIST", Message.MessageType.SYSTEM));
        } else if (content.startsWith("/deleterole "))

        {
            if (DatabaseManager.hasPermission(this.username, "perm_manage_roles")) {
                String rName = content.substring(12).trim();
                // Prevent deleting Admin role?
                if ("Admin".equalsIgnoreCase(rName)) {
                    sendMessage(new Message("System", "Impossible de supprimer le rôle Admin.", "system",
                            Message.MessageType.SYSTEM));
                } else {
                    DatabaseManager.deleteRole(rName);
                    sendMessage(new Message("System", "Rôle " + rName + " supprimé.", "system",
                            Message.MessageType.SYSTEM));
                    sendRolesList(); // Update client
                }
            }
        } else if (content.startsWith("/status")) {
            String[] parts = content.split(" ");
            if (parts.length >= 2) {
                String newStatus = parts[1].toUpperCase();
                // Diffuser le statut à tous les clients connectés (global broadcast)
                Message statusMsg = new Message(this.username, newStatus, "GLOBAL", Message.MessageType.STATUS_UPDATE);
                for (ClientHandler client : Server.clients.values()) {
                    client.sendMessage(statusMsg);
                }
            }
        } else if (content.startsWith("/passwd")) {
            String[] parts = content.split(" ", 2);
            if (parts.length >= 2) {
                String newPass = parts[1];
                DatabaseManager.updatePassword(this.username, newPass);
                sendMessage(new Message("System", "Mot de passe mis à jour.", "system", Message.MessageType.SYSTEM));
            }
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

    private void sendRolesList() {
        List<String> roles = DatabaseManager.getAllRoles();
        String rolesStr = String.join(",", roles);
        sendMessage(new Message("System", rolesStr, "ROLES_LIST", Message.MessageType.SYSTEM));
    }

    private void joinChannel(String channelName) {
        if (currentChannel != null) {
            currentChannel.removeMember(this);
        }
        currentChannel = Server.getChannel(channelName);
        currentChannel.addMember(this);

        // Envoyer l'historique au nouveau membre (Optional TODO)
        // sendMessage(new Message("System", "Vous êtes maintenant dans #" +
        // channelName, channelName,
        // Message.MessageType.SYSTEM));
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
        // Create consistent DM channel name (alphabetically sorted)
        String dmChannel = getDMChannelName(this.username, targetUser);

        Message privMsg = new Message(username, content, dmChannel, Message.MessageType.PRIVATE);

        // Always save to database (history)
        DatabaseManager.saveMessage(privMsg);

        // Send to target if online
        ClientHandler target = Server.clients.get(targetUser);
        if (target != null) {
            target.sendMessage(privMsg);
        }

        // Echo to sender
        sendMessage(privMsg);
    }

    private static String getDMChannelName(String user1, String user2) {
        // Create consistent channel name regardless of order
        if (user1.compareTo(user2) < 0) {
            return "DM:" + user1 + ":" + user2;
        } else {
            return "DM:" + user2 + ":" + user1;
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

            // Notify all friends that this user went offline
            List<String> friends = DatabaseManager.getFriends(username);
            for (String friendName : friends) {
                ClientHandler friendHandler = Server.clients.get(friendName);
                if (friendHandler != null) {
                    // Send updated friend list to each online friend
                    List<String> theirFriends = DatabaseManager.getFriends(friendName);
                    StringBuilder sb = new StringBuilder();
                    for (String f : theirFriends) {
                        boolean online = Server.clients.containsKey(f);
                        if (sb.length() > 0)
                            sb.append(",");
                        sb.append(f).append(":").append(online ? "Online" : "Offline");
                    }
                    friendHandler.sendMessage(
                            new Message("System", sb.toString(), "friends", Message.MessageType.FRIEND_LIST));
                }
            }
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