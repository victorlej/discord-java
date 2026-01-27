package client;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // Look and feel moderne (flat)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Lancer dans l'EDT (Event Dispatch Thread)
        SwingUtilities.invokeLater(() -> {
            ChatController chat = new ChatController();
            chat.setVisible(true);
        });
    }
}