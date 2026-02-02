package client;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.*;
import java.util.function.Consumer;

public class VoiceManager {
    private static final int SERVER_PORT = 5001;
    private static final int BUFFER_SIZE = 1024;
    private String serverHost;
    private DatagramSocket socket;
    private boolean active = false;

    private TargetDataLine microphone;
    private SourceDataLine speakers;

    private Consumer<Double> levelListener;

    public void setLevelListener(Consumer<Double> listener) {
        this.levelListener = listener;
    }

    public VoiceManager(String serverHost) {
        this.serverHost = serverHost;
        System.out.println("[VoiceManager] Init with host: " + serverHost);
    }

    public void joinChannel(String channelName) {
        if (active) {
            leaveChannel();
        }

        try {
            socket = new DatagramSocket();
            active = true;

            // Audio Format: 8kHz, 16bit, Mono
            AudioFormat format = new AudioFormat(8000.0f, 16, 1, true, true);

            // Setup Microphone
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
            if (!AudioSystem.isLineSupported(micInfo)) {
                System.err.println("[VoiceManager] Microphone not supported!");
                return;
            }
            microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphone.open(format);
            microphone.start();
            System.out.println("[VoiceManager] Microphone started.");

            // Setup Speakers
            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(speakerInfo)) {
                System.err.println("[VoiceManager] Speakers not supported!");
                return;
            }
            speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speakers.open(format);
            speakers.start();
            System.out.println("[VoiceManager] Speakers started.");

            // Send Join Packet
            sendPacket('J', channelName.getBytes());
            System.out.println("[VoiceManager] Sent JOIN for channel: " + channelName);

            // Start Threads
            new Thread(this::captureAudio, "Voice-Capture").start();
            new Thread(this::playAudio, "Voice-Playback").start();

        } catch (Exception e) {
            e.printStackTrace();
            active = false;
        }
    }

    public void leaveChannel() {
        if (!active)
            return;
        active = false;

        try {
            sendPacket('L', new byte[0]);
            System.out.println("[VoiceManager] Sent LEAVE.");
        } catch (Exception e) {
            // Ignore
        }

        if (microphone != null)
            microphone.close();
        if (speakers != null)
            speakers.close();
        if (socket != null && !socket.isClosed())
            socket.close();
        System.out.println("[VoiceManager] Voice Disconnected.");
    }

    private void captureAudio() {
        byte[] audioData = new byte[900]; // Payload size

        System.out.println("[VoiceManager] Capture thread started.");
        while (active) {
            try {
                int bytesRead = microphone.read(audioData, 0, audioData.length);
                if (bytesRead > 0) {
                    // Normalize volume for viz if needed (optional)
                    if (levelListener != null) {
                        long sum = 0;
                        for (int i = 0; i < bytesRead; i += 2) {
                            if (i + 1 < bytesRead) {
                                int sample = (short) ((audioData[i] << 8) | (audioData[i + 1] & 0xFF));
                                sum += sample * sample;
                            }
                        }
                        double rms = Math.sqrt(sum / (bytesRead / 2.0));
                        double normalized = Math.min(100, (rms / 3000.0) * 100.0); // Adjusted scaling
                        levelListener.accept(normalized);
                    }

                    // Packet: 'A' + AudioData
                    byte[] packetData = new byte[bytesRead + 1];
                    packetData[0] = (byte) 'A'; // 'A' for Audio
                    System.arraycopy(audioData, 0, packetData, 1, bytesRead);

                    DatagramPacket packet = new DatagramPacket(
                            packetData,
                            packetData.length,
                            InetAddress.getByName(serverHost),
                            SERVER_PORT);
                    socket.send(packet);
                }
            } catch (IOException e) {
                if (active)
                    e.printStackTrace();
            }
        }
    }

    private void playAudio() {
        byte[] buffer = new byte[BUFFER_SIZE];

        System.out.println("[VoiceManager] Playback thread started.");
        while (active) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Blocking

                byte[] data = packet.getData();
                // System.out.println("[VoiceManager] Received packet len=" +
                // packet.getLength()); // Debug spam

                if (packet.getLength() > 1 && data[0] == 'A') {
                    speakers.write(data, 1, packet.getLength() - 1);
                }
            } catch (IOException e) {
                if (active)
                    e.printStackTrace();
            }
        }
    }

    private void sendPacket(char type, byte[] content) throws IOException {
        byte[] data = new byte[content.length + 1];
        data[0] = (byte) type;
        System.arraycopy(content, 0, data, 1, content.length);

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(serverHost),
                SERVER_PORT);
        socket.send(packet);
    }
}
