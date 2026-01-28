package server;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import common.Message;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:discord.db";

    public static void init() {
        try (Connection conn = DriverManager.getConnection(URL);
                Statement stmt = conn.createStatement()) {

            // Table USERS
            // username: identifiant unique
            // password: mot de passe
            // blocked: si l'utilisateur est bloqué
            // can_create_channel: permission de créer des salons
            String sqlUsers = "CREATE TABLE IF NOT EXISTS users (" +
                    "username TEXT PRIMARY KEY, " +
                    "password TEXT, " +
                    "blocked BOOLEAN DEFAULT 0, " +
                    "can_create_channel BOOLEAN DEFAULT 0" +
                    ");";
            stmt.execute(sqlUsers);

            // Migration: ajout colonne password
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN password TEXT");
            } catch (SQLException ignored) {
            }

            // Table CHANNELS
            String sqlChannels = "CREATE TABLE IF NOT EXISTS channels (" +
                    "name TEXT PRIMARY KEY, " +
                    "type TEXT DEFAULT 'TEXT'" +
                    ");";
            stmt.execute(sqlChannels);

            // Migration simple: ajout de la colonne type si elle manque (pour versions
            // précédentes)
            try {
                stmt.execute("ALTER TABLE channels ADD COLUMN type TEXT DEFAULT 'TEXT'");
            } catch (SQLException ignored) {
                // La colonne existe probablement déjà
            }

            // Table MESSAGES (Historique simple)
            String sqlMessages = "CREATE TABLE IF NOT EXISTS messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "channel TEXT, " +
                    "username TEXT, " +
                    "content TEXT, " +
                    "timestamp INTEGER, " +
                    "type TEXT DEFAULT 'CHAT', " +
                    "file_name TEXT, " +
                    "file_data BLOB" +
                    ");";
            stmt.execute(sqlMessages);

            // Migration pour supporter les fichiers si la table existe déjà
            try {
                stmt.execute("ALTER TABLE messages ADD COLUMN type TEXT DEFAULT 'CHAT'");
            } catch (SQLException ignored) {
            }
            try {
                stmt.execute("ALTER TABLE messages ADD COLUMN file_name TEXT");
            } catch (SQLException ignored) {
            }
            try {
                stmt.execute("ALTER TABLE messages ADD COLUMN file_data BLOB");
            } catch (SQLException ignored) {
            }

            // Insertion des salons par défaut si la table est vide
            if (getChannels().isEmpty()) {
                createChannel("general", "TEXT");
                createChannel("dev", "TEXT");
                createChannel("gaming", "TEXT");
                createChannel("vocal", "VOICE");
            }

            System.out.println("✅ Base de données initialisée (SQLite).");
        } catch (SQLException e) {
            System.err.println("Erreur init DB: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    // --- GESTION UTILISATEURS ---

    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users(username, password) VALUES(?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            // e.printStackTrace(); // Likely duplicate key
            return false;
        }
    }

    public static boolean authenticateUser(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String storedParams = rs.getString("password");
                // Compatibility with old users (no password) -> allow any or require set?
                // Let's assume old users effectively have no password or null.
                if (storedParams == null)
                    return true; // weak security for migration
                return storedParams.equals(password);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean userExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void setCanCreateChannel(String username, boolean canCreate) {
        String sql = "UPDATE users SET can_create_channel = ? WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, canCreate);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean canCreateChannel(String username) {
        String sql = "SELECT can_create_channel FROM users WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("can_create_channel");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void blockUser(String username, boolean blocked) {
        String sql = "UPDATE users SET blocked = ? WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, blocked);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean isBlocked(String username) {
        String sql = "SELECT blocked FROM users WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getBoolean("blocked");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void deleteUser(String username) {
        String sql = "DELETE FROM users WHERE username = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- GESTION SALONS ---

    public static void createChannel(String name) {
        createChannel(name, "TEXT");
    }

    public static void createChannel(String name, String type) {
        String sql = "INSERT OR IGNORE INTO channels(name, type) VALUES(?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.setString(2, type);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void deleteChannel(String name) {
        String sql = "DELETE FROM channels WHERE name = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void renameChannel(String oldName, String newName) {
        String sql = "UPDATE channels SET name = ? WHERE name = ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setString(2, oldName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static class ChannelData {
        public String name;
        public String type;

        public ChannelData(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    public static List<ChannelData> getChannels() {
        List<ChannelData> channels = new ArrayList<>();
        String sql = "SELECT name, type FROM channels";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                channels.add(new ChannelData(rs.getString("name"), rs.getString("type")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return channels;
    }

    // --- GESTION MESSAGES ---

    public static void saveMessage(Message msg) {
        String sql = "INSERT INTO messages(channel, username, content, timestamp, type, file_name, file_data) VALUES(?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, msg.getChannel());
            pstmt.setString(2, msg.getUsername());
            pstmt.setString(3, msg.getContent());
            pstmt.setLong(4, java.sql.Timestamp.valueOf(msg.getTimestamp()).getTime());
            pstmt.setString(5, msg.getType().name());
            pstmt.setString(6, msg.getFileName());
            pstmt.setBytes(7, msg.getFileData());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static List<Message> getLastMessages(String channelName, int limit) {
        List<Message> history = new ArrayList<>();
        String sql = "SELECT * FROM messages WHERE channel = ? ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, channelName);
            pstmt.setInt(2, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String user = rs.getString("username");
                String content = rs.getString("content");
                long ts = rs.getLong("timestamp");
                String typeStr = rs.getString("type");
                String fileName = rs.getString("file_name");
                byte[] fileData = rs.getBytes("file_data");
                java.time.LocalDateTime timestamp = new java.sql.Timestamp(ts).toLocalDateTime();

                Message.MessageType type = Message.MessageType.CHAT;
                if (typeStr != null) {
                    try {
                        type = Message.MessageType.valueOf(typeStr);
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                }

                if (type == Message.MessageType.FILE) {
                    history.add(new Message(user, fileName, fileData, channelName, type, timestamp));
                } else {
                    history.add(new Message(user, content, channelName, type, timestamp));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        Collections.reverse(history);
        return history;
    }
}
