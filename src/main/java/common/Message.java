package common;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private String username;
    private String content;
    private String channel;
    private LocalDateTime timestamp;
    private MessageType type;

    public enum MessageType {
        CHAT, SYSTEM, PRIVATE, FILE, USER_LIST, CHANNEL_LIST, CHANNEL_USERS, STATUS_UPDATE, SERVER_LIST, CREATE_SERVER
    }

    private byte[] fileData;
    private String fileName;

    public Message(String username, String content, String channel, MessageType type) {
        this(username, content, channel, type, LocalDateTime.now());
    }

    // Constructor for files
    public Message(String username, String fileName, byte[] fileData, String channel, MessageType type) {
        this(username, fileName, fileData, channel, type, LocalDateTime.now());
    }

    public Message(String username, String fileName, byte[] fileData, String channel, MessageType type,
            LocalDateTime timestamp) {
        this(username, fileName, channel, type, timestamp);
        this.fileData = fileData;
        this.fileName = fileName;
    }

    public Message(String username, String content, String channel, MessageType type, LocalDateTime timestamp) {
        this.username = username;
        this.content = content;
        this.channel = channel;
        this.timestamp = timestamp;
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public String getContent() {
        return content;
    }

    public String getChannel() {
        return channel;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public MessageType getType() {
        return type;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public String getFileName() {
        return fileName;
    }
}
