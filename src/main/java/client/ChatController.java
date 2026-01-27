package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionListener; // Import explicit
import javax.swing.text.*;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import common.Message;

public class ChatController extends JFrame {

    // Couleurs Discord
    private static final Color BG_DARK = new Color(54, 57, 63); // #36393f
    private static final Color BG_SIDEBAR = new Color(47, 49, 54); // #2f3136
    private static final Color BG_INPUT = new Color(64, 68, 75); // #40444b
    private static final Color ACCENT = new Color(88, 101, 242); // #5865f2
    private static final Color TEXT_NORMAL = new Color(220, 221, 222);
    private static final Color TEXT_GRAY = new Color(142, 146, 151);

    private JTextPane chatArea;
    private JTextField inputField;
    private JList<String> channelList;
    private JList<String> userList;
    private DefaultListModel<String> channelModel;
    private DefaultListModel<String> userModel;
    private StyledDocument chatDoc;

    private NetworkClient networkClient;
    private String currentUser;
    private String currentChannel = "general";

    public ChatController() {
        setTitle("Discord Java - Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        initComponents();
        layoutComponents();
        setupListeners();

        // Connexion au démarrage
        SwingUtilities.invokeLater(this::connectToServer);
    }

    private void initComponents() {
        // Zone de chat avec styles
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(BG_DARK);
        chatArea.setForeground(TEXT_NORMAL);
        chatDoc = chatArea.getStyledDocument();

        // Input
        inputField = new JTextField();
        inputField.setBackground(BG_INPUT);
        inputField.setForeground(TEXT_NORMAL);
        inputField.setCaretColor(Color.WHITE);
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(32, 34, 37)),
                new EmptyBorder(10, 15, 10, 15)));
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Listes
        channelModel = new DefaultListModel<>();
        channelModel.addElement("general");
        channelModel.addElement("dev");
        channelModel.addElement("gaming");
        channelList = new JList<>(channelModel);
        channelList.setBackground(BG_SIDEBAR);
        channelList.setForeground(TEXT_NORMAL);
        channelList.setSelectionBackground(new Color(66, 70, 77));
        channelList.setSelectionForeground(Color.WHITE);
        channelList.setFont(new Font("Segoe UI", Font.BOLD, 14));
        channelList.setCellRenderer(new ChannelRenderer());
        channelList.setSelectedIndex(0);

        userModel = new DefaultListModel<>();
        userModel.addElement("En attente...");
        userList = new JList<>(userModel);
        userList.setBackground(BG_SIDEBAR);
        userList.setForeground(TEXT_GRAY);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.setCellRenderer(new UserRenderer());
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());

        // Sidebar gauche (Salons)
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(BG_SIDEBAR);
        leftPanel.setPreferredSize(new Dimension(240, 0));
        leftPanel.setBorder(new EmptyBorder(20, 0, 0, 0));

        JLabel serverHeader = new JLabel("  SERVEUR JAVA");
        serverHeader.setForeground(TEXT_GRAY);
        serverHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JScrollPane channelScroll = new JScrollPane(channelList);
        channelScroll.setBorder(null);
        channelScroll.setBackground(BG_SIDEBAR);

        leftPanel.add(serverHeader, BorderLayout.NORTH);
        leftPanel.add(channelScroll, BorderLayout.CENTER);

        // Centre (Chat)
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(BG_DARK);

        // Header du canal
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(BG_DARK);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(32, 34, 37)));
        headerPanel.setPreferredSize(new Dimension(0, 50));

        channelLabel = new JLabel(" # " + currentChannel);
        channelLabel.setForeground(Color.WHITE);
        channelLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        channelLabel.setBorder(new EmptyBorder(0, 20, 0, 0));
        headerPanel.add(channelLabel, BorderLayout.WEST);

        // Zone messages
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(null);
        chatScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        chatScroll.getVerticalScrollBar().setBackground(BG_DARK);
        chatScroll.getVerticalScrollBar().setUI(new DarkScrollBarUI());

        // Panel input
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(BG_DARK);
        inputPanel.setBorder(new EmptyBorder(0, 20, 20, 20));
        inputPanel.add(inputField, BorderLayout.CENTER);

        JButton sendButton = new JButton("Envoyer");
        sendButton.setBackground(ACCENT);
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setBorder(new EmptyBorder(10, 20, 10, 20));
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.EAST);

        centerPanel.add(headerPanel, BorderLayout.NORTH);
        centerPanel.add(chatScroll, BorderLayout.CENTER);
        centerPanel.add(inputPanel, BorderLayout.SOUTH);

        // Sidebar droite (Utilisateurs)
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(BG_SIDEBAR);
        rightPanel.setPreferredSize(new Dimension(240, 0));
        rightPanel.setBorder(new EmptyBorder(20, 15, 0, 15));

        JLabel userHeader = new JLabel("EN LIGNE — 1");
        userHeader.setForeground(TEXT_GRAY);
        userHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(null);
        userScroll.setBackground(BG_SIDEBAR);

        rightPanel.add(userHeader, BorderLayout.NORTH);
        rightPanel.add(userScroll, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }

    private void setupListeners() {
        // Envoi avec Entrée
        inputField.addActionListener(e -> sendMessage());

        // Alternative: Bouton d'envoi explicite si nécessaire
        // (Le code actuel n'a pas de bouton visible dans le layout, seulement
        // inputField)

        // Changement de canal
        channelList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = channelList.getSelectedValue();
                if (selected != null && !selected.equals(currentChannel)) {
                    switchChannel(selected);
                }
            }
        });

        // Fermeture propre
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (networkClient != null)
                    networkClient.disconnect();
            }
        });
    }

    private void connectToServer() {
        // Création d'un panneau personnalisé pour demander Pseudo + IP
        JPanel panel = new JPanel(new GridLayout(0, 1));
        JTextField pseudoField = new JTextField("User");
        JTextField ipField = new JTextField("localhost");
        panel.add(new JLabel("Pseudo:"));
        panel.add(pseudoField);
        panel.add(new JLabel("Adresse IP du serveur:"));
        panel.add(ipField);

        int result = JOptionPane.showConfirmDialog(this, panel,
                "Connexion au serveur", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String username = pseudoField.getText();
            String host = ipField.getText();

            if (username == null || username.trim().isEmpty()) {
                System.exit(0);
            }

            currentUser = username;
            setTitle("Discord Java - " + username);

            // Ajouter l'utilisateur à la liste
            userModel.clear();
            userModel.addElement(username);

            networkClient = new NetworkClient(host, 5000, username, this);
            new Thread(networkClient).start();

            addSystemMessage("Connecté en tant que " + username);
            addSystemMessage("Bienvenue sur Discord Java ! Commandes: /join, /msg, /list");
        } else {
            System.exit(0);
        }
    }

    public void displayMessage(Message msg) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Style pour le nom d'utilisateur
                SimpleAttributeSet userStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(userStyle,
                        msg.getUsername().equals(currentUser) ? ACCENT : new Color(46, 204, 113));
                StyleConstants.setFontFamily(userStyle, "Segoe UI");
                StyleConstants.setBold(userStyle, true);
                StyleConstants.setFontSize(userStyle, 14);

                // Style pour l'heure
                SimpleAttributeSet timeStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(timeStyle, TEXT_GRAY);
                StyleConstants.setFontSize(timeStyle, 11);

                // Style pour le texte
                SimpleAttributeSet textStyle = new SimpleAttributeSet();
                StyleConstants.setForeground(textStyle,
                        msg.getType() == Message.MessageType.SYSTEM ? TEXT_GRAY : TEXT_NORMAL);
                StyleConstants.setFontSize(textStyle, 14);

                // Insertion
                if (msg.getType() == Message.MessageType.SYSTEM) {
                    chatDoc.insertString(chatDoc.getLength(),
                            "[System] " + msg.getContent() + "\n\n", textStyle);
                } else {
                    String time = msg.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm"));
                    chatDoc.insertString(chatDoc.getLength(), msg.getUsername(), userStyle);
                    chatDoc.insertString(chatDoc.getLength(), "  " + time + "\n", timeStyle);
                    chatDoc.insertString(chatDoc.getLength(), msg.getContent() + "\n\n", textStyle);

                    // Notification sonore (si pas moi)
                    if (!msg.getUsername().equals(currentUser)) {
                        Toolkit.getDefaultToolkit().beep();
                    }
                }

                // Scroll automatique
                chatArea.setCaretPosition(chatDoc.getLength());

            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (!text.isEmpty() && networkClient != null) {
            Message msg = new Message(currentUser, text, currentChannel,
                    text.startsWith("/") ? Message.MessageType.SYSTEM : Message.MessageType.CHAT);
            networkClient.sendMessage(msg);
            inputField.setText("");
        }
    }

    private JLabel channelLabel;

    private void switchChannel(String newChannel) {
        currentChannel = newChannel;
        // Update Header
        if (channelLabel != null) {
            channelLabel.setText(" # " + currentChannel);
        }

        networkClient.sendCommand("/join " + newChannel);
        chatArea.setText("");
        addSystemMessage("Vous avez rejoint #" + newChannel);
    }

    public void addSystemMessage(String text) {
        displayMessage(new Message("System", text, currentChannel, Message.MessageType.SYSTEM));
    }

    public void updateUserList(String[] users) {
        SwingUtilities.invokeLater(() -> {
            userModel.clear();
            for (String user : users) {
                userModel.addElement(user);
            }
        });
    }

    // Renderer personnalisé pour les salons
    private static class ChannelRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, "# " + value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(8, 15, 8, 10));
            return label;
        }
    }

    // Renderer pour les utilisateurs avec indicateur vert
    private static class UserRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
            panel.setBackground(isSelected ? new Color(66, 70, 77) : BG_SIDEBAR);

            // Indicateur vert
            JPanel indicator = new JPanel();
            indicator.setPreferredSize(new Dimension(8, 8));
            indicator.setBackground(new Color(46, 204, 113)); // Vert online
            indicator.setBorder(BorderFactory.createLineBorder(BG_SIDEBAR, 2));

            JLabel name = new JLabel(value.toString());
            name.setForeground(TEXT_NORMAL);
            name.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            panel.add(indicator);
            panel.add(name);
            return panel;
        }
    }

    // UI pour scrollbar sombre
    private static class DarkScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(32, 34, 37);
            trackColor = BG_DARK;
        }
    }
}