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
        CHAT, SYSTEM, PRIVATE, FILE, USER_LIST
    }

    public Message(String username, String content, String channel, MessageType type) {
        this.username = username;
        this.content = content;
        this.channel = channel;
        this.timestamp = LocalDateTime.now();
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
}
