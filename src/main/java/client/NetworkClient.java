package client;

import common.Message;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;

public class NetworkClient implements Runnable {

    private String host;
    private int port;
    private String username;
    private ChatController controller;
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private boolean running;

    public NetworkClient(String host, int port, String username, ChatController controller) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.controller = controller;
    }

    @Override
    public void run() {
        try {
            socket = new Socket(host, port);
            output = new ObjectOutputStream(socket.getOutputStream());
            output.flush();
            input = new ObjectInputStream(socket.getInputStream());

            // Handshake
            // 1. Receive "Enter pseudo"
            try {
                Message handShakeRequest = (Message) input.readObject();
                // We ignore the content, just assume it's the prompt.

                // 2. Send username
                output.writeObject(new Message(username, "", "auth", Message.MessageType.SYSTEM));
                output.flush();

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
                return;
            }

            running = true;

            // Listen loop
            while (running) {
                try {
                    Message msg = (Message) input.readObject();
                    handleMessage(msg);
                } catch (EOFException e) {
                    controller.addSystemMessage("Serveur déconnecté (EOF).");
                    running = false;
                } catch (SocketException e) {
                    if (running)
                        controller.addSystemMessage("Connexion perdue.");
                    running = false;
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            controller.addSystemMessage("Impossible de se connecter au serveur: " + e.getMessage());
            e.printStackTrace();
        } finally {
            disconnect();
        }
    }

    private void handleMessage(Message msg) {
        if (msg.getType() == Message.MessageType.USER_LIST) {
            String usersCsv = msg.getContent();
            String[] users = usersCsv.split(",");
            controller.updateUserList(users);
        } else {
            controller.displayMessage(msg);
        }
    }

    public void sendMessage(Message msg) {
        try {
            if (output != null) {
                output.writeObject(msg);
                output.flush();
            }
        } catch (IOException e) {
            controller.addSystemMessage("Erreur d'envoi du message.");
            e.printStackTrace();
        }
    }

    public void sendCommand(String cmd) {
        // If command is like /join, we wrap it in a system message or chat message
        // depending on server logic?
        // Server handles /join in "content" of message regardless of type, but
        // ClientHandler logic:
        // "Message msg = (Message) input.readObject(); handleCommand(msg);"
        // handleCommand uses msg.getContent().
        // So we can send a SYSTEM or CHAT message with the command in content.

        sendMessage(new Message(username, cmd, "system", Message.MessageType.SYSTEM));
    }

    public void disconnect() {
        running = false;
        try {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
