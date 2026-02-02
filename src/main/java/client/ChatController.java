package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URL; // Added URL import
import java.util.Properties;
import java.io.FileOutputStream;
import common.Message;

public class ChatController extends JFrame {

    // Couleurs Discord
    private static final Color BG_DARK = new Color(54, 57, 63); // #36393f
    private static final Color BG_SIDEBAR = new Color(47, 49, 54); // #2f3136
    private static final Color BG_INPUT = new Color(64, 68, 75); // #40444b
    private static final Color ACCENT = new Color(88, 101, 242); // #5865f2
    private static final Color ACCENT_HOVER = new Color(71, 82, 196); // Darker accent
    private static final Color TEXT_NORMAL = new Color(220, 221, 222);
    private static final Color TEXT_GRAY = new Color(142, 146, 151);
    private static final Color HOVER_BG = new Color(57, 60, 67); // Subtile hover for lists

    private int hoveredChannelIndex = -1;
    private int hoveredUserIndex = -1;

    private JTextPane chatArea;
    private RoundedTextField inputField; // Changed to custom component
    private JList<ChannelItem> channelList;
    private JList<String> userList;
    private DefaultListModel<ChannelItem> channelModel;
    private DefaultListModel<String> userModel;
    private StyledDocument chatDoc;

    private NetworkClient networkClient;
    private VoiceManager voiceManager; // Voice Manager
    private String currentUser;
    private String currentChannel = "general";

    // Voice UI components
    private JPanel centerPanel;
    private CardLayout centerLayout;
    private JPanel chatPanel;
    private JPanel voicePanel;
    private DefaultListModel<String> voiceUsersModel;
    private JList<String> voiceUsersList;
    private JProgressBar micLevelBar;

    private static final String CONFIG_FILE = "client_config.properties";

    public ChatController() {
        setTitle("Discord Java - Client (v2.0)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        // Initial setup only, voiceManager must be initialized after host is known

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

        // Input moderne
        inputField = new RoundedTextField(15);
        inputField.setBackground(BG_INPUT);
        inputField.setForeground(TEXT_NORMAL);
        inputField.setCaretColor(Color.WHITE);
        inputField.setBorder(new EmptyBorder(10, 15, 10, 15));
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        // Listes
        channelModel = new DefaultListModel<>();
        // Channels will be loaded from server
        // channelModel.addElement(new ChannelItem("general", "TEXT"));

        channelList = new JList<>(channelModel);
        channelList.setBackground(BG_SIDEBAR);
        channelList.setForeground(TEXT_NORMAL);
        channelList.setSelectionBackground(new Color(66, 70, 77));
        channelList.setSelectionForeground(Color.WHITE);
        channelList.setFont(new Font("Segoe UI", Font.BOLD, 14));
        channelList.setCellRenderer(new ChannelRenderer());
        channelList.setFixedCellHeight(35); // Hauteur fixe pour aérer
        // channelList.setSelectedIndex(0); // Don't select until loaded

        // Hover effect listener for channels
        channelList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = channelList.locationToIndex(e.getPoint());
                if (index != hoveredChannelIndex) {
                    hoveredChannelIndex = index;
                    channelList.repaint();
                }
            }
        });
        channelList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredChannelIndex = -1;
                channelList.repaint();
            }
        });

        userModel = new DefaultListModel<>();
        userModel.addElement("En attente...");
        userList = new JList<>(userModel);
        userList.setBackground(BG_SIDEBAR);
        userList.setForeground(TEXT_GRAY);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.setCellRenderer(new UserRenderer());
        userList.setFixedCellHeight(40); // Plus d'espace pour les users

        // Hover effect listener for users
        userList.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int index = userList.locationToIndex(e.getPoint());
                if (index != hoveredUserIndex) {
                    hoveredUserIndex = index;
                    userList.repaint();
                }
            }
        });
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoveredUserIndex = -1;
                userList.repaint();
            }
        });
        // Voice Panel Lists
        voiceUsersModel = new DefaultListModel<>();
        voiceUsersList = new JList<>(voiceUsersModel);
        voiceUsersList.setBackground(BG_DARK);
        voiceUsersList.setForeground(TEXT_NORMAL);
        voiceUsersList.setFont(new Font("Segoe UI", Font.BOLD, 16));
        voiceUsersList.setCellRenderer(new UserRenderer()); // Reuse renderer or create new one
        voiceUsersList.setFixedCellHeight(50);
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

        JButton addChannelBtn = new JButton("+");
        addChannelBtn.setForeground(TEXT_GRAY);
        addChannelBtn.setBorder(null);
        addChannelBtn.setContentAreaFilled(false);
        addChannelBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        addChannelBtn.setToolTipText("Créer un salon");
        addChannelBtn.addActionListener(e -> showCreateChannelDialog());

        JPanel headerContainer = new JPanel(new BorderLayout());
        headerContainer.setBackground(BG_SIDEBAR);
        headerContainer.add(serverHeader, BorderLayout.CENTER);
        headerContainer.add(addChannelBtn, BorderLayout.EAST);

        // Bouton Paramètres (Engrenage)
        JButton settingsBtn = new JButton("⚙");
        settingsBtn.setForeground(TEXT_GRAY);
        settingsBtn.setBorder(null);
        settingsBtn.setContentAreaFilled(false);
        settingsBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        settingsBtn.setToolTipText("Gestion des Rôles");
        settingsBtn.addActionListener(e -> showRoleManager());

        JPanel topButtons = new JPanel(new GridLayout(1, 2));
        topButtons.setBackground(BG_SIDEBAR);
        topButtons.add(addChannelBtn);
        topButtons.add(settingsBtn);

        headerContainer.add(topButtons, BorderLayout.EAST);
        headerContainer.setBorder(new EmptyBorder(0, 0, 10, 10)); // add padding

        JScrollPane channelScroll = new JScrollPane(channelList);
        channelScroll.setBorder(null);
        channelScroll.setBackground(BG_SIDEBAR);
        channelScroll.getVerticalScrollBar().setUI(new SleekScrollBarUI());
        channelScroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

        leftPanel.add(headerContainer, BorderLayout.NORTH);
        leftPanel.add(channelScroll, BorderLayout.CENTER);

        // Create Chat Panel Components (Header, Scroll, Input)
        // ----------------------------------------------------

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
        chatScroll.getVerticalScrollBar().setUI(new SleekScrollBarUI());
        chatScroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));

        // Panel input
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(BG_DARK);
        inputPanel.setBorder(new EmptyBorder(0, 20, 20, 20));

        // Share Button (+)
        JButton shareBtn = new JButton("+");
        shareBtn.setBackground(BG_INPUT);
        shareBtn.setForeground(Color.WHITE);
        shareBtn.setFont(new Font("Segoe UI", Font.BOLD, 20));
        shareBtn.setBorder(new EmptyBorder(0, 15, 0, 15));
        shareBtn.setFocusPainted(false);
        shareBtn.setContentAreaFilled(false);
        shareBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        shareBtn.addActionListener(e -> showShareMenu(shareBtn));

        inputPanel.add(shareBtn, BorderLayout.WEST);
        inputPanel.add(inputField, BorderLayout.CENTER);

        JButton sendButton = new ModernButton("Envoyer");
        sendButton.setBorder(new EmptyBorder(10, 20, 10, 20));
        sendButton.addActionListener(e -> sendMessage());
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Split center into CardLayout
        centerLayout = new CardLayout();
        centerPanel = new JPanel(centerLayout);
        centerPanel.setBackground(BG_DARK);

        // 1. Chat View
        chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBackground(BG_DARK);

        chatPanel.add(headerPanel, BorderLayout.NORTH);
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        // 2. Voice View
        voicePanel = new JPanel(new BorderLayout());
        voicePanel.setBackground(BG_DARK);

        JPanel voiceHeader = new JPanel(new BorderLayout());
        voiceHeader.setBackground(BG_DARK); // Darker
        voiceHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(32, 34, 37)));
        voiceHeader.setPreferredSize(new Dimension(0, 50));

        JLabel voiceLabel = new JLabel(" 🔊 Salon Vocal");
        voiceLabel.setForeground(Color.WHITE);
        voiceLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        voiceLabel.setBorder(new EmptyBorder(0, 20, 0, 0));
        voiceHeader.add(voiceLabel, BorderLayout.WEST);

        JPanel voiceContent = new JPanel(new BorderLayout());
        voiceContent.setBackground(BG_DARK);
        voiceContent.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel infoLabel = new JLabel("Utilisateurs connectés :");
        infoLabel.setForeground(TEXT_GRAY);
        infoLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        infoLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

        JScrollPane voiceScroll = new JScrollPane(voiceUsersList);
        voiceScroll.setBorder(null);
        voiceScroll.setBackground(BG_DARK);

        voiceContent.add(infoLabel, BorderLayout.NORTH);
        voiceContent.add(voiceScroll, BorderLayout.CENTER);

        voicePanel.add(voiceHeader, BorderLayout.NORTH);
        voicePanel.add(voiceContent, BorderLayout.CENTER);

        // Mic Level Bar (Bottom)
        JPanel micPanel = new JPanel(new BorderLayout());
        micPanel.setBackground(BG_DARK);
        micPanel.setBorder(new EmptyBorder(10, 20, 20, 20));

        micLevelBar = new JProgressBar(0, 100);
        micLevelBar.setValue(0);
        micLevelBar.setStringPainted(true);
        micLevelBar.setString("Test Micro");
        micLevelBar.setForeground(new Color(46, 204, 113)); // Green
        micLevelBar.setBackground(new Color(32, 34, 37));
        micLevelBar.setBorderPainted(false);

        micPanel.add(micLevelBar, BorderLayout.CENTER);
        voicePanel.add(micPanel, BorderLayout.SOUTH);

        centerPanel.add(chatPanel, "CHAT");
        centerPanel.add(voicePanel, "VOICE");

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
        userScroll.getVerticalScrollBar().setUI(new SleekScrollBarUI());
        userScroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

        rightPanel.add(userHeader, BorderLayout.NORTH);
        rightPanel.add(userScroll, BorderLayout.CENTER);

        // Add proper panel
        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    } // End of layoutComponents

    private void setupListeners() {
        // Envoi avec Entrée
        inputField.addActionListener(e -> sendMessage());

        // Menu contextuel pour les utilisateurs
        // (Initialisation supprimée ici car déplacée dans méthode dynamique)

        userList.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                check(e);
            }

            public void mouseReleased(MouseEvent e) {
                check(e);
            }

            private void check(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    int index = userList.locationToIndex(e.getPoint());
                    if (index != -1) {
                        userList.setSelectedIndex(index);
                        String selectedUser = userList.getModel().getElementAt(index);
                        if (!selectedUser.equals(currentUser)) {
                            showUserContextMenu(selectedUser, e.getX(), e.getY());
                        }
                    }
                }
            }
        });

        // Initialisation de l'icône vocale
        try {
            voiceIcon = new ImageIcon(getClass().getResource("voice_icon.png"));
            if (voiceIcon.getImageLoadStatus() != MediaTracker.COMPLETE) {
                // Try absolute path fallback if resource fails (dev env specific)
                voiceIcon = new ImageIcon("src/main/java/client/voice_icon.png");
            }
            // Scale
            Image img = voiceIcon.getImage();
            Image newImg = img.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
            voiceIcon = new ImageIcon(newImg);
        } catch (Exception e) {
            // Fallback silent
            System.err.println("Icone vocale non chargée: " + e.getMessage());
        }

        // Changement de canal
        channelList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                ChannelItem selected = channelList.getSelectedValue();
                if (selected != null && !selected.name.equals(currentChannel)) {
                    if (selected.type.equals("VOICE")) {
                        currentChannel = selected.name;
                        networkClient.sendCommand("/join " + selected.name);

                        // Connect Voice Manager
                        if (voiceManager != null) {
                            voiceManager.joinChannel(selected.name);
                        }

                        // Switch UI
                        channelLabel.setText(" 🔊 " + currentChannel);
                        centerLayout.show(centerPanel, "VOICE");
                        voiceUsersModel.clear();
                        addSystemMessage("🔊 Connexion au salon vocal " + selected.name + "...");
                    } else {
                        centerLayout.show(centerPanel, "CHAT");
                        switchChannel(selected.name);
                    }
                }
            }
        });

        // Context Menu for Channels
        JPopupMenu channelMenu = new JPopupMenu();
        JMenuItem renameItem = new JMenuItem("Renommer");
        JMenuItem deleteChanItem = new JMenuItem("Supprimer");

        renameItem.addActionListener(e -> {
            ChannelItem selected = channelList.getSelectedValue();
            if (selected != null) {
                String newName = JOptionPane.showInputDialog(this, "Nouveau nom pour #" + selected.name + ":");
                if (newName != null && !newName.trim().isEmpty()) {
                    networkClient.sendCommand("/renamechannel " + selected.name + " " + newName.trim());
                }
            }
        });

        deleteChanItem.addActionListener(e -> {
            ChannelItem selected = channelList.getSelectedValue();
            if (selected != null) {
                int confirm = JOptionPane.showConfirmDialog(this, "Supprimer #" + selected.name + " ?",
                        "Confirmer suppression", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    networkClient.sendCommand("/deletechannel " + selected.name);
                }
            }
        });

        channelMenu.add(renameItem);
        channelMenu.add(deleteChanItem);

        channelList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int row = channelList.locationToIndex(e.getPoint());
                    channelList.setSelectedIndex(row);
                    if (row != -1) {
                        channelMenu.show(channelList, e.getX(), e.getY());
                    }
                }
            }
        });

        // Fermeture propre
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                if (networkClient != null)
                    networkClient.disconnect();
            }
        });
    }

    private void connectToServer() {
        Properties props = loadConfig();
        String savedUser = props.getProperty("username", "");
        String savedHost = props.getProperty("host", "localhost");

        JDialog loginDialog = new JDialog(this, "Connexion", true);
        loginDialog.setUndecorated(true);
        loginDialog.setSize(400, 450);
        loginDialog.setLocationRelativeTo(null);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 2),
                new EmptyBorder(30, 40, 30, 40)));

        JLabel titleLabel = new JLabel("Bienvenue !");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subLabel = new JLabel("Connectez-vous ou créez un compte.");
        subLabel.setForeground(TEXT_GRAY);
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Fields Panel
        JPanel fieldsPanel = new JPanel();
        fieldsPanel.setLayout(new BoxLayout(fieldsPanel, BoxLayout.Y_AXIS));
        fieldsPanel.setBackground(BG_DARK);

        JLabel userLabel = new JLabel("NOM D'UTILISATEUR");
        userLabel.setForeground(TEXT_GRAY);
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        userLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField pseudoField = new RoundedTextField(10);
        pseudoField.setText(savedUser);
        pseudoField.setForeground(TEXT_NORMAL);
        pseudoField.setCaretColor(Color.WHITE);
        pseudoField.setBackground(BG_INPUT);
        pseudoField.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        pseudoField.setMaximumSize(new Dimension(500, 35));

        JLabel passLabel = new JLabel("MOT DE PASSE");
        passLabel.setForeground(TEXT_GRAY);
        passLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        passLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPasswordField passField = new JPasswordField();
        passField.setForeground(TEXT_NORMAL);
        passField.setCaretColor(Color.WHITE);
        passField.setBackground(BG_INPUT);
        passField.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        passField.setMaximumSize(new Dimension(500, 35));

        JLabel hostLabel = new JLabel("ADRESSE IP");
        hostLabel.setForeground(TEXT_GRAY);
        hostLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        hostLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField ipField = new RoundedTextField(10);
        ipField.setText(savedHost);
        ipField.setForeground(TEXT_NORMAL);
        ipField.setCaretColor(Color.WHITE);
        ipField.setBackground(BG_INPUT);
        ipField.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        ipField.setMaximumSize(new Dimension(500, 35));

        fieldsPanel.add(userLabel);
        fieldsPanel.add(Box.createVerticalStrut(5));
        fieldsPanel.add(pseudoField);
        fieldsPanel.add(Box.createVerticalStrut(10));
        fieldsPanel.add(passLabel);
        fieldsPanel.add(Box.createVerticalStrut(5));
        fieldsPanel.add(passField);
        fieldsPanel.add(Box.createVerticalStrut(10));
        fieldsPanel.add(hostLabel);
        fieldsPanel.add(Box.createVerticalStrut(5));
        fieldsPanel.add(ipField);

        // Buttons
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        btnPanel.setBackground(BG_DARK);
        btnPanel.setMaximumSize(new Dimension(500, 40));

        JButton loginBtn = new ModernButton("Connexion");
        JButton registerBtn = new ModernButton("S'inscrire");
        registerBtn.setBackground(new Color(60, 63, 68)); // Darker for optional action

        // Close button
        JButton closeBtn = new JButton("X");
        closeBtn.setForeground(TEXT_GRAY);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.setAlignmentX(Component.RIGHT_ALIGNMENT);
        closeBtn.addActionListener(e -> System.exit(0));

        // State holder
        final String[] authMode = { "LOGIN" }; // Default

        loginBtn.addActionListener(e -> {
            authMode[0] = "LOGIN";
            loginDialog.dispose();
        });

        registerBtn.addActionListener(e -> {
            authMode[0] = "REGISTER";
            loginDialog.dispose();
        });

        btnPanel.add(loginBtn);
        btnPanel.add(registerBtn);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_DARK);
        header.add(closeBtn, BorderLayout.EAST);
        header.setMaximumSize(new Dimension(500, 30));

        panel.add(header);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(5));
        panel.add(subLabel);
        panel.add(Box.createVerticalStrut(20));
        panel.add(fieldsPanel);
        panel.add(Box.createVerticalStrut(20));
        panel.add(btnPanel);

        loginDialog.add(panel);
        loginDialog.setVisible(true);

        String username = pseudoField.getText().trim();
        String password = new String(passField.getPassword());
        String host = ipField.getText().trim();

        if (username.isEmpty()) {
            System.exit(0);
        }

        saveConfig(username, host);

        currentUser = username;
        setTitle("Discord Java - " + username + " (v2.0)");

        userModel.clear();
        userModel.addElement(username);

        // Update NetworkClient constructor call
        networkClient = new NetworkClient(host, 5000, username, password, authMode[0], this);

        // Init Voice
        voiceManager = new VoiceManager(host);
        voiceManager.setLevelListener(this::updateMicLevel);

        new Thread(networkClient).start();

        addSystemMessage("Tentative de " + (authMode[0].equals("LOGIN") ? "connexion" : "création de compte") + "...");
    }

    private Properties loadConfig() {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
        } catch (IOException e) {
            // ignore
        }
        return props;
    }

    private void saveConfig(String user, String host) {
        Properties props = new Properties();
        props.setProperty("username", user);
        props.setProperty("host", host);
        try (FileOutputStream out = new FileOutputStream(CONFIG_FILE)) {
            props.store(out, "Client Configuration");
        } catch (IOException e) {
            e.printStackTrace();
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
                } else if (msg.getType() == Message.MessageType.FILE) {
                    // Affichage spécial pour les fichiers/images
                    chatDoc.insertString(chatDoc.getLength(), msg.getUsername(), userStyle);
                    chatDoc.insertString(chatDoc.getLength(),
                            "  " + msg.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm")) + "\n", timeStyle);

                    if (isImageFile(msg.getFileName())) {
                        if (msg.getFileData() != null) {
                            ImageIcon icon = new ImageIcon(msg.getFileData());
                            // Scaling simple
                            Image img = icon.getImage();
                            if (icon.getIconWidth() > 400) {
                                img = img.getScaledInstance(400, -1, Image.SCALE_SMOOTH);
                                icon = new ImageIcon(img);
                            }

                            // Container pour Image
                            JPanel container = new JPanel();
                            container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS)); // Vertical
                            container.setOpaque(false);
                            container.setAlignmentX(Component.LEFT_ALIGNMENT);

                            JLabel imgLabel = new JLabel(icon);
                            imgLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                            imgLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                            imgLabel.setToolTipText("Cliquer pour agrandir");

                            // Capture message for lambda
                            Message msgRef = msg;
                            imgLabel.addMouseListener(new MouseAdapter() {
                                @Override
                                public void mouseClicked(MouseEvent e) {
                                    showImagePreview(msgRef);
                                }
                            });

                            container.add(imgLabel);
                            container.add(Box.createVerticalStrut(5));
                            // Bouton téléchargement retiré ici pour épuré, disponible dans l'aperçu

                            chatArea.setCaretPosition(chatDoc.getLength());
                            chatArea.insertComponent(container);
                        } else {
                            chatDoc.insertString(chatDoc.getLength(), "[Image: " + msg.getFileName() + "]\n",
                                    textStyle);
                        }
                    } else {
                        // Container Fichier
                        JPanel container = new JPanel();
                        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
                        container.setOpaque(false);
                        container.setAlignmentX(Component.LEFT_ALIGNMENT);

                        // Panel Info Fichier
                        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
                        infoPanel.setBackground(new Color(47, 49, 54)); // BG sombre
                        infoPanel.setBorder(new javax.swing.border.LineBorder(new Color(32, 34, 37), 1, true));
                        infoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

                        JLabel fileLabel = new JLabel("📁 " + msg.getFileName() + " (" +
                                (msg.getFileData() != null ? msg.getFileData().length / 1024 + " KB" : "?") + ")");
                        fileLabel.setForeground(TEXT_NORMAL);
                        fileLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                        infoPanel.add(fileLabel);
                        container.add(infoPanel);
                        container.add(Box.createVerticalStrut(5));

                        JButton downloadBtn = new ModernButton("⬇ Télécharger le fichier");
                        downloadBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
                        downloadBtn.setBorder(new EmptyBorder(5, 10, 5, 10));
                        downloadBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                        downloadBtn.addActionListener(ev -> saveFile(msg));
                        container.add(downloadBtn);

                        chatArea.setCaretPosition(chatDoc.getLength());
                        chatArea.insertComponent(container);
                    }
                    chatDoc.insertString(chatDoc.getLength(), "\n", textStyle);

                    if (!msg.getUsername().equals(currentUser)) {
                        Toolkit.getDefaultToolkit().beep();
                    }
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
        // Détection du type de salon
        String type = "TEXT";
        for (int i = 0; i < channelModel.getSize(); i++) {
            ChannelItem item = channelModel.get(i);
            if (item.name.equals(newChannel)) {
                type = item.type;
                break;
            }
        }

        if (type.equals("VOICE")) {
            voiceManager.joinChannel(newChannel);
        } else {
            voiceManager.leaveChannel();
        }

        currentChannel = newChannel;

        // Update Header
        if (channelLabel != null) {
            channelLabel.setText((type.equals("VOICE") ? " 🔊 " : " # ") + currentChannel);
        }

        networkClient.sendCommand("/join " + newChannel);

        // Clear chat area if text
        if (type.equals("TEXT")) {
            chatArea.setText("");
            addSystemMessage("Vous avez rejoint #" + newChannel);
        } else {
            addSystemMessage("Vous avez rejoint le salon vocal: " + newChannel);
        }
    }

    public void addSystemMessage(String text) {
        displayMessage(new Message("System", text, currentChannel, Message.MessageType.SYSTEM));
    }

    private void updateMicLevel(Double level) {
        SwingUtilities.invokeLater(() -> {
            if (micLevelBar != null) {
                micLevelBar.setValue(level.intValue());
            }
        });
    }

    public void updateUserList(String[] users) {
        SwingUtilities.invokeLater(() -> {
            userModel.clear();
            for (String user : users) {
                userModel.addElement(user);
            }
        });
    }

    // Renderer personnalisé pour les salons avec HOVER et TYPE
    private class ChannelRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            ChannelItem item = (ChannelItem) value;
            String prefix = item.type.equals("VOICE") ? "🎙 " : "# ";
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, prefix + item.name, index, isSelected, cellHasFocus);

            label.setBorder(new EmptyBorder(0, 15, 0, 10)); // Padding ajusté

            if (isSelected) {
                label.setBackground(new Color(66, 70, 77));
                label.setForeground(Color.WHITE);
            } else if (index == hoveredChannelIndex) {
                label.setBackground(HOVER_BG);
                label.setForeground(TEXT_NORMAL);
            } else {
                label.setBackground(BG_SIDEBAR);
                label.setForeground(TEXT_GRAY);
            }
            return label;
        }
    }

    public void updateChannelList(String[] channelsData) {
        SwingUtilities.invokeLater(() -> {
            channelModel.clear();
            boolean currentFound = false;
            for (String s : channelsData) {
                String[] parts = s.split(":");
                String name = parts[0];
                String type = parts.length > 1 ? parts[1] : "TEXT";
                channelModel.addElement(new ChannelItem(name, type));
                if (name.equals(currentChannel))
                    currentFound = true;
            }
            // If current channel invalid (deleted), switch to general or first
            if (!currentFound && !channelModel.isEmpty()) {
                // Logic to switch back if current channel lost?
                // For now, keep it simple
            }
        });
    }

    public void updateVoiceUsers(String[] users) {
        SwingUtilities.invokeLater(() -> {
            voiceUsersModel.clear();
            for (String user : users) {
                voiceUsersModel.addElement(user);
            }
        });
    }

    private void showCreateChannelDialog() {
        JDialog dialog = new JDialog(this, "Créer un salon", true);
        dialog.setUndecorated(true);
        dialog.setSize(400, 250);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG_DARK);

        ((JPanel) dialog.getContentPane()).setBorder(BorderFactory.createLineBorder(ACCENT, 1));

        JPanel main = new JPanel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.setBackground(BG_DARK);
        main.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("CRÉER UN SALON");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel typeLabel = new JLabel("TYPE DE SALON");
        typeLabel.setForeground(TEXT_GRAY);
        typeLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        typeLabel.setBorder(new EmptyBorder(15, 0, 5, 0));
        typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JRadioButton textRadio = new JRadioButton("Textuel"); // Custom styling needed potentially, but lets stick to
                                                              // simpler
        textRadio.setBackground(BG_DARK);
        textRadio.setForeground(TEXT_NORMAL);
        JRadioButton voiceRadio = new JRadioButton("Vocal");
        voiceRadio.setBackground(BG_DARK);
        voiceRadio.setForeground(TEXT_NORMAL);

        ButtonGroup group = new ButtonGroup();
        group.add(textRadio);
        group.add(voiceRadio);
        textRadio.setSelected(true);

        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        radioPanel.setBackground(BG_DARK);
        radioPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        radioPanel.add(textRadio);
        radioPanel.add(voiceRadio);

        JLabel nameLabel = new JLabel("NOM DU SALON");
        nameLabel.setForeground(TEXT_GRAY);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        nameLabel.setBorder(new EmptyBorder(10, 0, 5, 0));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField nameField = new RoundedTextField(10);
        nameField.setBackground(BG_INPUT);
        nameField.setForeground(TEXT_NORMAL);
        nameField.setCaretColor(Color.WHITE);
        nameField.setBorder(new EmptyBorder(5, 10, 5, 10));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameField.setMaximumSize(new Dimension(500, 35));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(BG_DARK);
        btnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton cancelBtn = new JButton("Annuler");
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setContentAreaFilled(false);
        cancelBtn.setBorderPainted(false);
        cancelBtn.addActionListener(e -> dialog.dispose());

        JButton createBtn = new ModernButton("Créer");
        createBtn.setPreferredSize(new Dimension(100, 35));
        createBtn.addActionListener(e -> {
            String name = nameField.getText().trim().replace(" ", "-").toLowerCase();
            if (!name.isEmpty()) {
                String type = voiceRadio.isSelected() ? "VOICE" : "TEXT";
                networkClient.sendCommand("/create " + name + " " + type);
                dialog.dispose();
            }
        });

        btnPanel.add(cancelBtn);
        btnPanel.add(createBtn);

        main.add(title);
        main.add(typeLabel);
        main.add(radioPanel);
        main.add(nameLabel);
        main.add(nameField);
        main.add(Box.createVerticalStrut(20));
        main.add(btnPanel);

        dialog.add(main);
        dialog.setVisible(true);
    }

    private void showShareMenu(Component invoker) {
        JPopupMenu menu = new JPopupMenu();
        // Panel interne pour ajouter du padding et contrôler le style
        JPanel container = new JPanel(new GridLayout(1, 3, 10, 10));
        container.setBackground(BG_SIDEBAR);
        container.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton photoBtn = createRoundButton("📷", "Photos");
        photoBtn.addActionListener(e -> selectAndSendFile(true));

        JButton fileBtn = createRoundButton("📁", "Fichiers");
        fileBtn.addActionListener(e -> selectAndSendFile(false));

        JButton emojiBtn = createRoundButton("😊", "Emojis");
        emojiBtn.addActionListener(e -> showEmojiPicker(invoker));

        container.add(photoBtn);
        container.add(fileBtn);
        container.add(emojiBtn);

        menu.add(container);

        menu.show(invoker, 0, -60); // Show above
    }

    private void selectAndSendFile(boolean imagesOnly) {
        JFileChooser chooser = new JFileChooser();
        if (imagesOnly) {
            chooser.setFileFilter(
                    new javax.swing.filechooser.FileNameExtensionFilter("Images", "jpg", "png", "gif", "jpeg"));
        }
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (file.length() > 5 * 1024 * 1024) { // 5MB limit
                JOptionPane.showMessageDialog(this, "Fichier trop volumineux (Max 5MB)");
                return;
            }
            new Thread(() -> {
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] data = new byte[(int) file.length()];
                    fis.read(data);
                    Message msg = new Message(currentUser, file.getName(), data, currentChannel,
                            Message.MessageType.FILE);
                    networkClient.sendMessage(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                    addSystemMessage("Erreur lecture fichier: " + e.getMessage());
                }
            }).start();
        }
    }

    private void showEmojiPicker(Component invoker) {
        JWindow emojiWindow = new JWindow(SwingUtilities.getWindowAncestor(invoker));

        JPanel content = new JPanel(new GridLayout(5, 6, 2, 2));
        content.setBackground(new Color(47, 49, 54)); // BG_SIDEBAR for better visibility
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(32, 34, 37), 1),
                new EmptyBorder(5, 5, 5, 5)));

        String[] emojis = {
                "😀", "😃", "😄", "😁", "😆", "😅",
                "😂", "🤣", "😊", "😇", "🙂", "🙃",
                "😉", "😌", "😍", "🥰", "😘", "😗",
                "👍", "👎", "👌", "✌️", "🤞", "🤟",
                "❤️", "🧡", "💛", "💚", "💙", "💜"
        };

        for (String emoji : emojis) {
            // Utilisation d'images en ligne pour les emojis "jolis" (Twemoji)
            // Fallback sur texte si echec de chargement
            String hex = Integer.toHexString(emoji.codePointAt(0));
            String url = "https://raw.githubusercontent.com/twitter/twemoji/master/assets/72x72/" + hex + ".png";

            JButton btn = new JButton(emoji); // Texte par défaut
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));

            // Tentative de chargement asynchrone pour ne pas bloquer l'UI
            new Thread(() -> {
                try {
                    URL u = URI.create(url).toURL();
                    ImageIcon icon = new ImageIcon(
                            new ImageIcon(u).getImage().getScaledInstance(32, 32, Image.SCALE_SMOOTH));
                    SwingUtilities.invokeLater(() -> {
                        btn.setText("");
                        btn.setIcon(icon);
                    });
                } catch (Exception ex) {
                    // keep text
                }
            }).start();

            btn.setForeground(Color.WHITE);
            btn.setBackground(new Color(47, 49, 54));

            // Explicitly set opaque for some LAFs
            btn.setOpaque(true);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    btn.setBackground(new Color(88, 101, 242));
                }

                public void mouseExited(MouseEvent e) {
                    btn.setBackground(new Color(47, 49, 54));
                }
            });

            btn.addActionListener(e -> {
                inputField.setText(inputField.getText() + emoji);
                emojiWindow.dispose();
            });
            content.add(btn);
        }

        emojiWindow.add(content);
        emojiWindow.pack();

        Point loc = invoker.getLocationOnScreen();
        // Position above the button
        emojiWindow.setLocation(loc.x, loc.y - emojiWindow.getHeight() - 10);
        emojiWindow.setVisible(true);

        // Auto-close on focus lost
        emojiWindow.setFocusable(true);
        emojiWindow.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                emojiWindow.dispose();
            }
        });
        emojiWindow.requestFocus();
    }

    private boolean isImageFile(String name) {
        if (name == null)
            return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".jpeg");
    }

    private JButton createRoundButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(new Dimension(50, 50));
        btn.setMargin(new Insets(0, 0, 0, 0)); // Fix truncation
        btn.setToolTipText(tooltip);

        btn.setBackground(BG_INPUT); // Dark grey background
        btn.setForeground(Color.WHITE);

        btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20)); // Slightly smaller to fit
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);

        // Add hover effect
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(ACCENT);
            }

            public void mouseExited(MouseEvent e) {
                btn.setBackground(BG_INPUT);
            }
        });

        return btn;
    }

    public static class ChannelItem {
        String name;
        String type;

        public ChannelItem(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public String toString() {
            return name;
        }
    }

    // Renderer pour les utilisateurs avec indicateur vert et HOVER
    private class UserRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10)); // Layout aéré

            if (isSelected) {
                panel.setBackground(new Color(66, 70, 77));
            } else if (index == hoveredUserIndex) {
                panel.setBackground(HOVER_BG);
            } else {
                panel.setBackground(BG_SIDEBAR);
            }

            // Indicateur vert
            JPanel indicator = new JPanel();
            indicator.setPreferredSize(new Dimension(10, 10));
            indicator.setBackground(new Color(46, 204, 113)); // Vert online
            // Petit contour pour que ça ressorte
            indicator.setBorder(null);

            // Shape ronde pour l'indicateur (hacky via paintComponent si on voulait, mais
            // JPanel carré est ok pour l'instant
            // ou on peut faire un JPanel custom)

            JLabel name = new JLabel(value.toString());
            name.setForeground(isSelected ? Color.WHITE : (index == hoveredUserIndex ? TEXT_NORMAL : TEXT_GRAY));
            name.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            panel.add(indicator);
            panel.add(name);
            return panel;
        }
    }

    // --- COMPOSANTS MODERNES ---

    private static class RoundedTextField extends JTextField {
        private int radius;

        public RoundedTextField(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            super.paintComponent(g2);
            g2.dispose();
        }

        @Override
        protected void paintBorder(Graphics g) {
            // Pas de bordure ou bordure subtile
        }
    }

    private static class ModernButton extends JButton {
        private Color targetBackground;
        private float animationProgress = 0f;
        private Timer hoverTimer;

        public ModernButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setBackground(ACCENT);
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            targetBackground = ACCENT;

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    startAnimation(ACCENT_HOVER);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    startAnimation(ACCENT);
                }
            });
        }

        private void startAnimation(Color target) {
            targetBackground = target;
            if (hoverTimer != null && hoverTimer.isRunning())
                hoverTimer.stop();

            hoverTimer = new Timer(20, e -> {
                Color current = getBackground();
                int r = approach(current.getRed(), targetBackground.getRed());
                int g = approach(current.getGreen(), targetBackground.getGreen());
                int b = approach(current.getBlue(), targetBackground.getBlue());

                setBackground(new Color(r, g, b));
                repaint();

                if (r == targetBackground.getRed() && g == targetBackground.getGreen()
                        && b == targetBackground.getBlue()) {
                    ((Timer) e.getSource()).stop();
                }
            });
            hoverTimer.start();
        }

        private int approach(int current, int target) {
            int step = 5;
            if (current < target)
                return Math.min(current + step, target);
            if (current > target)
                return Math.max(current - step, target);
            return current;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

            super.paintComponent(g);
            g2.dispose();
        }
    }

    private void saveFile(Message msg) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(msg.getFileName()));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                java.nio.file.Files.write(chooser.getSelectedFile().toPath(), msg.getFileData());
                JOptionPane.showMessageDialog(this, "Fichier enregistré !");
            } catch (java.io.IOException e) {
                JOptionPane.showMessageDialog(this, "Erreur sauvegarde: " + e.getMessage());
            }
        }
    }

    private static class SleekScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(32, 34, 37);
            trackColor = BG_SIDEBAR; // Fond transparent/similaire
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private JButton createZeroButton() {
            JButton btn = new JButton();
            btn.setPreferredSize(new Dimension(0, 0));
            btn.setMinimumSize(new Dimension(0, 0));
            btn.setMaximumSize(new Dimension(0, 0));
            return btn;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            // Forme arrondie et plus fine à l'intérieur
            g2.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, 10, 10);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            // Track invisible ou discret
        }
    }

    private void showImagePreview(Message msg) {
        if (msg.getFileData() == null)
            return;

        JDialog previewDialog = new JDialog(this, "Aperçu - " + msg.getFileName(), true);
        previewDialog.setLayout(new BorderLayout());
        previewDialog.setSize(800, 600);
        previewDialog.setLocationRelativeTo(this);
        previewDialog.getContentPane().setBackground(new Color(32, 34, 37));

        // Image
        ImageIcon icon = new ImageIcon(msg.getFileData());
        // Auto-scale if too big for screen?
        // For now, let's just show it in a scroll pane centered.
        JLabel label = new JLabel(icon);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);

        JScrollPane scrollPane = new JScrollPane(label);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(32, 34, 37));
        previewDialog.add(scrollPane, BorderLayout.CENTER);

        // Overlay / Toolbar for Download
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        topPanel.setBackground(new Color(0, 0, 0, 100)); // Semi-transparent black
        // Actually simple non-overlay toolbar is easier and cleaner
        topPanel.setBackground(new Color(47, 49, 54));

        JButton downloadBtn = new JButton("⬇"); // Juste un icone (well, unicode char here, could use image)
        downloadBtn.setToolTipText("Télécharger");
        downloadBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        downloadBtn.setBorderPainted(false);
        downloadBtn.setContentAreaFilled(false);
        downloadBtn.setForeground(Color.WHITE);
        downloadBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        downloadBtn.addActionListener(e -> saveFile(msg));

        topPanel.add(downloadBtn);
        previewDialog.add(topPanel, BorderLayout.NORTH);

        previewDialog.setVisible(true);
    }

    private void showUserContextMenu(String username, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(BG_DARK);
        menu.setBorder(BorderFactory.createLineBorder(Color.GRAY));

        JMenuItem title = new JMenuItem("Gérer: " + username);
        title.setEnabled(false);
        menu.add(title);
        menu.addSeparator();

        JMenu rolesMenu = new JMenu("Attribuer Rôle");
        rolesMenu.setBackground(BG_DARK);
        rolesMenu.setForeground(TEXT_NORMAL);

        // Hardcoded basic roles map for context menu (In a real app, query server for
        // available roles)
        String[] possibleRoles = { "Admin", "Modérateur", "Membre" };

        for (String role : possibleRoles) {
            JMenuItem roleItem = new JMenuItem(role);
            roleItem.addActionListener(e -> {
                networkClient.sendCommand("/assignrole " + username + " " + role);
            });
            rolesMenu.add(roleItem);
        }
        menu.add(rolesMenu);

        JMenuItem kickItem = new JMenuItem("Expulser");
        kickItem.addActionListener(e -> networkClient.sendCommand("/kick " + username));
        menu.add(kickItem);

        JMenuItem blockItem = new JMenuItem("Bloquer");
        blockItem.addActionListener(e -> networkClient.sendCommand("/block " + username));
        menu.add(blockItem);

        menu.show(userList, x, y);
    }

    // --- INTERFACE GESTION ROLES ---
    private void showRoleManager() {
        JDialog roleDialog = new JDialog(this, "Gestion des Rôles", true);
        roleDialog.setSize(500, 400);
        roleDialog.setLocationRelativeTo(this);
        roleDialog.setLayout(new BorderLayout());
        roleDialog.getContentPane().setBackground(BG_DARK);

        JPanel formPanel = new JPanel(new GridLayout(0, 1, 10, 10));
        formPanel.setBackground(BG_DARK);
        formPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JTextField nameField = new RoundedTextField(20);
        nameField.setText("NouveauRole");

        JCheckBox chkCreate = new JCheckBox("Créer des salons");
        JCheckBox chkBlock = new JCheckBox("Bloquer / Expulser");
        JCheckBox chkDel = new JCheckBox("Supprimer des messages");
        JCheckBox chkManage = new JCheckBox("Gérer les rôles (Admin)");

        styleCheckBox(chkCreate);
        styleCheckBox(chkBlock);
        styleCheckBox(chkDel);
        styleCheckBox(chkManage);

        formPanel.add(label("Nom du rôle :"));
        formPanel.add(nameField);
        formPanel.add(chkCreate);
        formPanel.add(chkBlock);
        formPanel.add(chkDel);
        formPanel.add(chkManage);

        JButton saveBtn = new ModernButton("Créer / Mettre à jour");
        saveBtn.addActionListener(e -> {
            String rName = nameField.getText().trim();
            if (rName.isEmpty())
                return;

            // Construit la commande sans espaces
            // /createrole Name 1 0 1 0
            String cmd = String.format("/createrole %s %b %b %b %b",
                    rName,
                    chkCreate.isSelected(),
                    chkBlock.isSelected(),
                    chkDel.isSelected(),
                    chkManage.isSelected());

            networkClient.sendCommand(cmd);
            roleDialog.dispose();
        });

        roleDialog.add(formPanel, BorderLayout.CENTER);
        roleDialog.add(saveBtn, BorderLayout.SOUTH);
        roleDialog.setVisible(true);
    }

    private void styleCheckBox(JCheckBox chk) {
        chk.setBackground(BG_DARK);
        chk.setForeground(TEXT_NORMAL);
        chk.setFocusPainted(false);
        chk.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_GRAY);
        return l;
    }

    private ImageIcon voiceIcon;
}