package server;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceServer implements Runnable {
    private static final int PORT = 5001;
    private static final int BUFFER_SIZE = 1024;

    private Map<String, Set<SocketAddress>> channels = new ConcurrentHashMap<>();
    private Map<SocketAddress, String> clientChannels = new ConcurrentHashMap<>();

    private DatagramSocket socket;
    private boolean running;

    public VoiceServer() {
        try {
            // Bind to all interfaces (0.0.0.0)
            socket = new DatagramSocket(PORT);
            running = true;
            System.out.println("🎤 Serveur Vocal (UDP) démarré sur le port " + PORT);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // WAITING for data

                SocketAddress sender = packet.getSocketAddress();
                byte[] data = packet.getData();
                int len = packet.getLength();

                if (len < 1)
                    continue;

                char type = (char) data[0]; // First byte is type

                if (type == 'J') { // JOIN
                    String channelName = new String(data, 1, len - 1).trim();
                    System.out.println("[VoiceServer] Client " + sender + " joining " + channelName);
                    handleJoin(sender, channelName);
                } else if (type == 'L') { // LEAVE
                    System.out.println("[VoiceServer] Client " + sender + " leaving");
                    handleLeave(sender);
                } else if (type == 'A') { // AUDIO
                    // System.out.println("[VoiceServer] Audio from " + sender); // Spammy
                    handleAudio(sender, packet);
                } else if (type == 'T') { // TALK STATUS
                    handleForward(sender, packet);
                }

            } catch (IOException e) {
                if (running)
                    e.printStackTrace();
            }
        }
    }

    private void handleJoin(SocketAddress sender, String channelName) {
        // Force leave ANY channel before joining new one
        String current = clientChannels.get(sender);
        if (current != null && !current.equals(channelName)) {
            handleLeave(sender);
        } else if (current != null && current.equals(channelName)) {
            return; // Already in this channel, ignore? Or update?
        }

        handleLeave(sender); // Just to be safe/clean
        channels.computeIfAbsent(channelName, k -> Collections.synchronizedSet(new HashSet<>())).add(sender);
        clientChannels.put(sender, channelName);

        // Broadcast user list update via ClientHandler/Server mechanisms?
        // Voice Server is separate (UDP), doesn't easily talk to TCP Server.
        // We rely on Client calling /join X on TCP side to update user lists.
    }

    private void handleLeave(SocketAddress sender) {
        String channel = clientChannels.remove(sender);
        if (channel != null) {
            Set<SocketAddress> set = channels.get(channel);
            if (set != null) {
                set.remove(sender);
                if (set.isEmpty()) {
                    channels.remove(channel);
                }
            }
        }
    }

    private void handleAudio(SocketAddress sender, DatagramPacket originalPacket) {
        handleForward(sender, originalPacket);
    }

    private void handleForward(SocketAddress sender, DatagramPacket originalPacket) {
        String channel = clientChannels.get(sender);
        if (channel != null) {
            Set<SocketAddress> recipients = channels.get(channel);
            if (recipients != null) {
                synchronized (recipients) {
                    for (SocketAddress recipient : recipients) {
                        // Forward to everyone ELSE.
                        if (!recipient.equals(sender)) {
                            try {
                                DatagramPacket forward = new DatagramPacket(
                                        originalPacket.getData(),
                                        originalPacket.getLength(),
                                        recipient);
                                socket.send(forward);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
