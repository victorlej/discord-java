package client;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.File;
import java.net.*;
import java.util.function.Consumer;

public class VoiceManager {
    private static final int SERVER_PORT = 5001;
    private static final int BUFFER_SIZE = 1024;
    private String serverHost;
    private DatagramSocket socket;
    private boolean active = false;
    private boolean muted = false;
    private boolean deafened = false;

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isDeafened() {
        return deafened;
    }

    public void setDeafened(boolean deafened) {
        this.deafened = deafened;
    }

    public boolean isActive() {
        return active;
    }

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

    private String username;

    public void joinChannel(String channelName, String username) {
        this.username = username;
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
                    // When muted, suppress everything: level, talking status, and audio
                    if (muted) {
                        // Show zero level when muted
                        if (levelListener != null) {
                            levelListener.accept(0.0);
                        }
                        // Force talking status to false when muted
                        if (isTalking) {
                            isTalking = false;
                            if (talkingListener != null) {
                                talkingListener.accept(username, false);
                            }
                            try {
                                byte[] status = new byte[1];
                                status[0] = (byte) '0';
                                sendPacket('T', status);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        continue; // Skip all audio processing when muted
                    }

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
                        // Increased sensitivity: normalized relative to 500.0 instead of 2000.0
                        double normalized = Math.min(100, (rms / 500.0) * 100.0);

                        // Debug log every ~1 sec (approx every 20 packets)
                        if (System.currentTimeMillis() % 1000 < 50) {
                            System.out.println("[VoiceManager] RMS: " + (int) rms + " -> Level: " + (int) normalized);
                        }

                        levelListener.accept(normalized);

                        // Detect Talking Status Transition
                        boolean nowTalking = normalized > 10.0;
                        if (nowTalking != isTalking) {
                            isTalking = nowTalking;
                            // Notify local listener immediately for UI update
                            if (talkingListener != null) {
                                talkingListener.accept(username, isTalking);
                            }
                            try {
                                // Send TALK status packet: 'T' + 1 or 0
                                byte[] status = new byte[1];
                                status[0] = (byte) (isTalking ? '1' : '0');
                                sendPacket('T', status);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    // Send audio data
                    byte[] validAudio = new byte[bytesRead];
                    System.arraycopy(audioData, 0, validAudio, 0, bytesRead);
                    sendPacket('A', validAudio);
                }
            } catch (IOException e) {
                if (active)
                    e.printStackTrace();
            }
        }
    }

    // Track talking state to avoid spamming packets
    private boolean isTalking = false;

    private java.util.function.BiConsumer<String, Boolean> talkingListener;

    public void setTalkingListener(java.util.function.BiConsumer<String, Boolean> listener) {
        this.talkingListener = listener;
    }

    private void playAudio() {
        byte[] buffer = new byte[BUFFER_SIZE];

        System.out.println("[VoiceManager] Playback thread started.");
        while (active) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Blocking

                byte[] data = packet.getData();
                int len = packet.getLength();

                if (len < 2)
                    continue; // Too short

                char type = (char) data[0];
                int nameLen = data[1] & 0xFF; // Unsigned byte

                if (len < 2 + nameLen)
                    continue; // Invalid

                String senderName = new String(data, 2, nameLen);
                int payloadStart = 2 + nameLen;
                int payloadLen = len - payloadStart;

                if (type == 'A') {
                    if (payloadLen > 0 && !deafened)
                        speakers.write(data, payloadStart, payloadLen);
                } else if (type == 'T') {
                    // Parse '1' or '0'
                    if (payloadLen > 0) {
                        boolean isTalking = (data[payloadStart] == '1');
                        if (talkingListener != null) {
                            talkingListener.accept(senderName, isTalking);
                        }
                    }
                }
            } catch (IOException e) {
                if (active)
                    e.printStackTrace();
            }
        }
    }

    public void playSoundEffect(String soundName) {
        if (!active) return;

        new Thread(() -> {
            try {
                // Cherche le fichier dans resources/sounds/ ou dossier local sounds/
                String filename = soundName + ".wav";
                URL url = getClass().getResource("/sounds/" + filename);
                
                if (url == null) {
                    File f = new File("sounds/" + filename);
                    if (f.exists()) {
                        url = f.toURI().toURL();
                    }
                }

                // Fallback: Cherche à la racine du projet (ex: rahhh.wav directement à côté de src)
                if (url == null) {
                    File f = new File(filename);
                    if (f.exists()) {
                        url = f.toURI().toURL();
                    }
                }

                if (url == null) {
                    System.err.println("[VoiceManager] Son introuvable: " + filename + " (Vérifiez qu'il est à la racine ou dans le dossier 'sounds')");
                    return;
                }
                System.out.println("[VoiceManager] Lecture du son: " + url);

                AudioInputStream audioIn = AudioSystem.getAudioInputStream(url);
                AudioFormat targetFormat = new AudioFormat(8000.0f, 16, 1, true, true);
                
                if (!audioIn.getFormat().matches(targetFormat)) {
                    if (AudioSystem.isConversionSupported(targetFormat, audioIn.getFormat())) {
                        audioIn = AudioSystem.getAudioInputStream(targetFormat, audioIn);
                    }
                }

                byte[] buffer = new byte[900];
                int bytesRead;
                long sleep = (long) (900.0 / 16000.0 * 1000.0); // Throttle sending

                // Configuration d'une ligne audio locale pour entendre son propre son
                SourceDataLine localSfxLine = null;
                try {
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, targetFormat);
                    if (AudioSystem.isLineSupported(info)) {
                        localSfxLine = (SourceDataLine) AudioSystem.getLine(info);
                        localSfxLine.open(targetFormat);
                        localSfxLine.start();
                    }
                } catch (Exception ex) {
                    System.err.println("[VoiceManager] Impossible de jouer le son localement: " + ex.getMessage());
                }

                while ((bytesRead = audioIn.read(buffer)) != -1 && active) {
                    byte[] data = new byte[bytesRead];
                    System.arraycopy(buffer, 0, data, 0, bytesRead);
                    sendPacket('A', data);
                    if (localSfxLine != null) {
                        localSfxLine.write(data, 0, bytesRead);
                    }
                    Thread.sleep(sleep);
                }
                if (localSfxLine != null) { localSfxLine.drain(); localSfxLine.close(); }
                audioIn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendPacket(char type, byte[] content) throws IOException {
        byte[] nameBytes = username.getBytes();
        int nameLen = nameBytes.length;

        byte[] data = new byte[1 + 1 + nameLen + content.length];
        data[0] = (byte) type;
        data[1] = (byte) nameLen;
        System.arraycopy(nameBytes, 0, data, 2, nameLen);
        System.arraycopy(content, 0, data, 2 + nameLen, content.length);

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                InetAddress.getByName(serverHost),
                SERVER_PORT);
        socket.send(packet);
    }
}
