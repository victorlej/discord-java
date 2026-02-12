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
import java.net.URL;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.io.FileOutputStream;
import common.Message;

public class ChatController extends JFrame {

    // Couleurs Discord
    private static final Color BG_DARK = new Color(54, 57, 63); // #36393f
    private static final Color BG_SIDEBAR = new Color(47, 49, 54); // #2f3136
    private static final Color BG_INPUT = new Color(64, 68, 75); // #40444b
    private static final Color ACCENT = new Color(88, 101, 242); // #5865f2

    private static final Color TEXT_NORMAL = new Color(220, 221, 222);
    private static final Color TEXT_GRAY = new Color(142, 146, 151);
    private static final Color HOVER_BG = new Color(57, 60, 67); // Subtile hover for lists

    private int hoveredChannelIndex = -1;
    private int hoveredUserIndex = -1;
    private Map<String, Boolean> talkingStates = new HashMap<>();
    private Map<String, String> userStatuses = new HashMap<>(); // Stores status: ONLINE, IDLE, DND

    private JTextPane chatArea;
    private RoundedTextField inputField; // Changed to custom component
    private VoiceManager voiceManager; // Voice Manager
    private String currentUser;

    public String getCurrentUser() {
        return currentUser;
    }

    private String currentChannel = "general";
    private String currentServer = "Main Server";
    private java.util.Map<String, java.util.List<ChannelItem>> serverChannels = new java.util.HashMap<>();
    private JPanel serverListPanel;
    private java.util.Map<String, ModernComponents.ServerButton> serverButtons = new java.util.HashMap<>();

    // Sidebar Model
    private DefaultListModel<SidebarItem> channelModel;
    private JList<SidebarItem> channelList;

    // Restored Fields
    private JList<String> userList;
    private DefaultListModel<String> userModel;
    private StyledDocument chatDoc;
    private NetworkClient networkClient;
    private JLabel serverHeader;
    private JLabel channelLabel;

    // Friends UI
    private JPanel friendsPanel;
    private DefaultListModel<String> friendsModel;
    private JList<String> friendsList;
    private JButton homeButton;
    private String userTag; // Stores the user's tag (e.g. 1234)

    // Sidebar Buttons
    private JButton addChannelBtn;
    private JButton settingsBtn;
    private JButton dmCallButton;

    public interface SidebarItem {
        boolean isChannel();

        String getName();
    }

    public static class ChannelItem implements SidebarItem {
        String name;
        String type;

        public ChannelItem(String name, String type) {
            this.name = name;
            this.type = type;
        }

        public boolean isChannel() {
            return true;
        }

        public String getName() {
            return name;
        }

        public String toString() {
            return name;
        }
    }

    public static class VoiceUserItem implements SidebarItem {
        String username;
        boolean isTalking = false; // New: Speaking status

        public VoiceUserItem(String username) {
            this.username = username;
        }

        public boolean isChannel() {
            return false;
        }

        public String getName() {
            return username;
        }

        public String toString() {
            return username;
        }

        public void setTalking(boolean talking) {
            this.isTalking = talking;
        }

    }

    // New Item for Private Messages
    public static class PrivateMessageItem implements SidebarItem {
        String username;
        String status;

        public PrivateMessageItem(String username, String status) {
            this.username = username;
            this.status = status;
        }

        public boolean isChannel() {
            return false;
        } // It's not a server channel

        public String getName() {
            return username;
        }

        public String getStatus() {
            return status;
        }

        public String toString() {
            return username;
        }
    }

    // Role Manager Model
    private DefaultListModel<String> roleListModel;

    // Voice UI components
    private JPanel centerPanel;
    private CardLayout centerLayout;
    private JPanel chatPanel;
    private JPanel voicePanel;
    private DefaultListModel<String> voiceUsersModel;
    private JList<String> voiceUsersList;
    private int currentMicLevel = 0;

    // Voice Sidebar Controls
    private JPanel voiceControlPanel;
    private JLabel voiceStatusLabel;

    private static final String CONFIG_FILE = "client_config.properties";

    public void updateUserStatus(String username, String status) {
        userStatuses.put(username, status);
        SwingUtilities.invokeLater(() -> {
            if (userList != null)
                userList.repaint();
            // Also update friends list status if friend is in list
            if (friendsModel != null) {
                // We need to re-render or update specific element
                friendsList.repaint();
            }
        });
    }

    public void updateFriendList(String[] friends) {
        SwingUtilities.invokeLater(() -> {
            friendsModel.clear();
            for (String f : friends) {
                friendsModel.addElement(f);
            }

            // If we're on the Home screen, also refresh the sidebar
            if (currentServer == null) {
                channelModel.clear();
                for (String f : friends) {
                    String[] parts = f.split(":");
                    channelModel.addElement(new PrivateMessageItem(parts[0], parts.length > 1 ? parts[1] : "Offline"));
                }
            }
        });
    }

    public void setMyTag(String tag) {
        this.userTag = tag;
    }

    private void showUserProfile() {
        JDialog dialog = new JDialog(this, "Mon Profil", true);
        dialog.setSize(400, 350);
        dialog.setLocationRelativeTo(this);
        dialog.getContentPane().setBackground(BG_DARK);
        dialog.setLayout(new BoxLayout(dialog.getContentPane(), BoxLayout.Y_AXIS));
        ((JPanel) dialog.getContentPane()).setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Profil de " + currentUser);
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Avatar Preview
        JLabel avatar = new JLabel(ModernComponents.generateAvatar(currentUser, 64));
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Show Tag directly
        String tagDisplay = currentUser + " #" + (userTag != null ? userTag : "????");
        JLabel tagLabel = new JLabel(tagDisplay);
        tagLabel.setForeground(new Color(185, 187, 190)); // Light Gray
        tagLabel.setFont(new Font("Segoe UI", Font.BOLD, 24)); // Big Font
        tagLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        tagLabel.setBorder(new EmptyBorder(10, 0, 10, 0));

        // If tag is unknown, request it
        if (userTag == null) {
            networkClient.sendCommand("/myid");
        }

        // Status Selector
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        statusPanel.setBackground(BG_DARK);
        statusPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(TEXT_GRAY), "Statut", 0, 0, new Font("Segoe UI", Font.PLAIN, 12),
                TEXT_GRAY));

        String[] statuses = { "En Ligne", "Absent", "Ne pas déranger" };
        JComboBox<String> statusBox = new JComboBox<>(statuses);
        statusBox.addActionListener(e -> {
            String selected = (String) statusBox.getSelectedItem();
            String code = "ONLINE";
            if (selected.equals("Absent"))
                code = "IDLE";
            if (selected.equals("Ne pas déranger"))
                code = "DND";
            networkClient.sendCommand("/status " + code);
            updateUserStatus(currentUser, code); // Optimistic update
        });
        statusPanel.add(statusBox);
        statusPanel.add(statusBox);
        // Tag label added to main dialog

        dialog.add(title);
        dialog.add(Box.createVerticalStrut(10));
        dialog.add(avatar);
        dialog.add(Box.createVerticalStrut(10));
        dialog.add(tagLabel);
        dialog.add(Box.createVerticalStrut(10));
        dialog.add(statusPanel); // Contains status selector
        dialog.add(Box.createVerticalStrut(10));

        // Password Change
        JPanel passPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        passPanel.setBackground(BG_DARK);
        passPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(TEXT_GRAY), "Changer le mot de passe", 0, 0,
                new Font("Segoe UI", Font.PLAIN, 12), TEXT_GRAY));

        JPasswordField newPass = new JPasswordField();
        JButton changeBtn = new ModernComponents.ModernButton("Valider");
        changeBtn.addActionListener(e -> {
            String p = new String(newPass.getPassword());
            if (!p.isEmpty()) {
                networkClient.sendCommand("/passwd " + p); // Simplified: just send new pass for now (demo)
                JOptionPane.showMessageDialog(dialog, "Demande envoyée.");
                newPass.setText("");
            }
        });

        passPanel.add(new JLabel("Nouveau :"));
        passPanel.add(newPass);
        passPanel.add(new JPanel() {
            {
                setOpaque(false);
            }
        }); // Spacer
        passPanel.add(changeBtn);

        dialog.add(passPanel);
        dialog.add(Box.createVerticalStrut(20));

        JButton closeBtn = new ModernComponents.ModernButton("Fermer");
        closeBtn.addActionListener(e -> dialog.dispose());

        dialog.add(closeBtn);
        dialog.setVisible(true);
    }

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

        // Drag & Drop Support
        new java.awt.dnd.DropTarget(this, new java.awt.dnd.DropTargetAdapter() {
            public void drop(java.awt.dnd.DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                    java.util.List<File> droppedFiles = (java.util.List<File>) dtde.getTransferable()
                            .getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor);

                    for (File file : droppedFiles) {
                        if (file.isDirectory())
                            continue;

                        // Confirm upload
                        int confirm = JOptionPane.showConfirmDialog(ChatController.this,
                                "Envoyer le fichier : " + file.getName() + " ?",
                                "Confirmation d'envoi", JOptionPane.YES_NO_OPTION);

                        if (confirm == JOptionPane.YES_OPTION) {
                            if (file.length() > 5 * 1024 * 1024) { // 5MB limit
                                JOptionPane.showMessageDialog(ChatController.this, "Fichier trop volumineux (Max 5MB)");
                                continue;
                            }
                            // Logic similar to selectAndSendFile
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
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
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
        inputField.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));

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
        channelList.setCellRenderer(new SidebarRenderer());
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
        voiceUsersList.setCellRenderer(new VoiceUserRenderer());
        voiceUsersList.setFixedCellHeight(60);

        // F. Friends List
        friendsModel = new DefaultListModel<>();
        friendsList = new JList<>(friendsModel);
        friendsList.setBackground(BG_DARK);
        friendsList.setForeground(TEXT_NORMAL);
        friendsList.setFont(new Font("Segoe UI", Font.BOLD, 14));
        // Use a simple renderer for now or reuse UserRenderer if compatible
        friendsList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                // value is string "pseudo:status"
                String str = (String) value;
                String[] parts = str.split(":");
                String name = parts[0];
                boolean online = parts.length > 1 && "Online".equals(parts[1]);

                JPanel p = new JPanel(new BorderLayout(10, 0));
                p.setBackground(isSelected ? new Color(57, 60, 67) : BG_DARK);
                p.setBorder(new EmptyBorder(10, 10, 10, 10));

                JLabel nameLbl = new JLabel(name);
                nameLbl.setForeground(Color.WHITE);
                nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));

                JLabel statusLbl = new JLabel(online ? "En ligne" : "Hors ligne");
                statusLbl.setForeground(online ? new Color(46, 204, 113) : TEXT_GRAY);
                statusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));

                p.add(nameLbl, BorderLayout.WEST);
                p.add(statusLbl, BorderLayout.EAST);
                return p;
            }
        });
    }

    private void layoutComponents() {
        setLayout(new BorderLayout());

        // Sidebar gauche (Salons)
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(BG_SIDEBAR);
        leftPanel.setPreferredSize(new Dimension(240, 0));
        leftPanel.setBorder(new EmptyBorder(20, 0, 0, 0));

        serverHeader = new JLabel("  SERVEUR JAVA");
        serverHeader.setForeground(TEXT_GRAY);
        serverHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));

        addChannelBtn = new JButton("+");
        addChannelBtn.setForeground(TEXT_GRAY);
        addChannelBtn.setFont(new Font("Segoe UI", Font.BOLD, 24)); // Taille augmentée
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
        settingsBtn = new JButton("⚙");
        settingsBtn.setForeground(TEXT_GRAY);
        settingsBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 22)); // Taille augmentée, Font Emoji
        settingsBtn.setBorder(null);
        settingsBtn.setContentAreaFilled(false);
        settingsBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        settingsBtn.setToolTipText("Gestion des Rôles");
        settingsBtn.addActionListener(e -> showRoleManager());

        JPanel topButtons = new JPanel(new GridLayout(1, 3, 5, 0)); // Un peu d'espacement
        topButtons.setBackground(BG_SIDEBAR);
        topButtons.add(addChannelBtn);

        JButton profileBtn = new JButton("👤");
        profileBtn.setForeground(TEXT_GRAY);
        profileBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));
        profileBtn.setBorder(null);
        profileBtn.setContentAreaFilled(false);
        profileBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        profileBtn.setToolTipText("Mon Profil");
        profileBtn.addActionListener(e -> showUserProfile());

        topButtons.add(profileBtn);
        topButtons.add(settingsBtn);

        headerContainer.add(topButtons, BorderLayout.EAST);
        headerContainer.setBorder(new EmptyBorder(0, 0, 10, 10)); // add padding

        JScrollPane channelScroll = new JScrollPane(channelList);
        channelScroll.setBorder(null);
        channelScroll.setBackground(BG_SIDEBAR);
        channelScroll.getVerticalScrollBar().setUI(new ModernComponents.SleekScrollBarUI());
        channelScroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

        leftPanel.add(headerContainer, BorderLayout.NORTH);
        leftPanel.add(channelScroll, BorderLayout.CENTER);

        // Voice Control Panel (Bottom Sidebar)
        voiceControlPanel = new JPanel(new BorderLayout());
        voiceControlPanel.setBackground(new Color(32, 34, 37)); // Darker than sidebar
        voiceControlPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(25, 27, 30)));
        voiceControlPanel.setVisible(false);
        voiceControlPanel.setPreferredSize(new Dimension(0, 50));

        JPanel voiceInfo = new JPanel(new GridLayout(2, 1));
        voiceInfo.setOpaque(false);
        voiceInfo.setBorder(new EmptyBorder(5, 10, 5, 0));

        JLabel voiceTitle = new JLabel("Connexion Vocale");
        voiceTitle.setForeground(new Color(46, 204, 113)); // Green
        voiceTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));

        voiceStatusLabel = new JLabel("Salon");
        voiceStatusLabel.setForeground(TEXT_NORMAL);
        voiceStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        voiceInfo.add(voiceTitle);
        voiceInfo.add(voiceStatusLabel);

        JButton sidebarHangUp = new JButton("❌");
        sidebarHangUp.setForeground(Color.RED);
        sidebarHangUp.setBorderPainted(false);
        sidebarHangUp.setContentAreaFilled(false);
        sidebarHangUp.setFocusPainted(false);
        sidebarHangUp.setFont(new Font("Segoe UI Emoji", Font.BOLD, 16));
        sidebarHangUp.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sidebarHangUp.addActionListener(e -> leaveVoiceChannel());

        voiceControlPanel.add(voiceInfo, BorderLayout.CENTER);
        voiceControlPanel.add(sidebarHangUp, BorderLayout.EAST);

        leftPanel.add(voiceControlPanel, BorderLayout.SOUTH);

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

        // Call button (visible in DM mode only)
        JButton dmCallBtn = createRoundButton("📞", "Appeler");
        dmCallBtn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 18));
        dmCallBtn.setVisible(false);
        dmCallBtn.addActionListener(e -> {
            if (currentDMUser != null) {
                networkClient.sendCommand("/call " + currentDMUser);
            }
        });

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        headerRight.setOpaque(false);
        headerRight.add(dmCallBtn);
        headerPanel.add(headerRight, BorderLayout.EAST);

        // Store reference for toggling visibility
        this.dmCallButton = dmCallBtn;

        // Zone messages
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(null);
        chatScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        chatScroll.getVerticalScrollBar().setBackground(BG_DARK);
        chatScroll.getVerticalScrollBar().setUI(new ModernComponents.SleekScrollBarUI());
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

        JButton sendButton = new ModernComponents.ModernButton("Envoyer");
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

        JButton headerHangUp = new JButton("❌");
        headerHangUp.setToolTipText("Raccrocher");
        headerHangUp.setBorderPainted(false);
        headerHangUp.setContentAreaFilled(false);
        headerHangUp.setForeground(Color.RED);
        headerHangUp.setFont(new Font("Segoe UI Emoji", Font.BOLD, 16));
        headerHangUp.addActionListener(ev -> leaveVoiceChannel());
        voiceHeader.add(headerHangUp, BorderLayout.EAST);

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

        // Controls Panel (Hang Up)
        JPanel controlsPanel = new JPanel();
        controlsPanel.setBackground(BG_DARK);
        controlsPanel.setBorder(new EmptyBorder(10, 0, 10, 0));

        JButton hangUpBtn = new JButton("Raccrocher");
        hangUpBtn.setBackground(new Color(231, 76, 60)); // Red
        hangUpBtn.setForeground(Color.WHITE);
        hangUpBtn.setFocusPainted(false);
        hangUpBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        hangUpBtn.addActionListener(e -> leaveVoiceChannel());

        controlsPanel.add(hangUpBtn);
        voicePanel.add(controlsPanel, BorderLayout.SOUTH);

        // Mic Level Bar (Bottom of Voice Panel)

        // 3. Friends View
        friendsPanel = new JPanel(new BorderLayout());
        friendsPanel.setBackground(BG_DARK);

        // Header Friends
        JPanel friendsHeader = new JPanel(new BorderLayout());
        friendsHeader.setBackground(BG_DARK);
        friendsHeader.setPreferredSize(new Dimension(0, 50));
        friendsHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(32, 34, 37)));

        JLabel friendsLabel = new JLabel("  Amis");
        friendsLabel.setForeground(Color.WHITE);
        friendsLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        friendsHeader.add(friendsLabel, BorderLayout.WEST);

        JButton addFriendBtn = new JButton("Ajouter un ami");
        addFriendBtn.setBackground(new Color(46, 204, 113));
        addFriendBtn.setForeground(Color.WHITE);
        addFriendBtn.setFocusPainted(false);
        addFriendBtn.setFont(new Font("Segoe UI", Font.BOLD, 14)); // Bigger font
        addFriendBtn.setBorder(new EmptyBorder(5, 15, 5, 15)); // Padding
        addFriendBtn.addActionListener(e -> {
            String target = JOptionPane.showInputDialog(this, "Entrez le pseudo#tag de l'ami :");
            if (target != null && !target.trim().isEmpty()) {
                networkClient.sendCommand("/friend add " + target.trim());
                // Small delay then refresh friend list
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ignored) {
                    }
                    networkClient.sendCommand("/friend list");
                }).start();
            }
        });
        // Wrapper for button to add padding
        JPanel btnWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnWrapper.setOpaque(false);
        btnWrapper.add(addFriendBtn);
        friendsHeader.add(btnWrapper, BorderLayout.EAST);

        friendsPanel.add(friendsHeader, BorderLayout.NORTH);

        JScrollPane friendScroll = new JScrollPane(friendsList);
        friendScroll.setBorder(null);
        friendScroll.setBackground(BG_DARK);
        friendsPanel.add(friendScroll, BorderLayout.CENTER);

        centerPanel.add(chatPanel, "CHAT");
        centerPanel.add(voicePanel, "VOICE");
        centerPanel.add(friendsPanel, "FRIENDS");

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
        userScroll.getVerticalScrollBar().setUI(new ModernComponents.SleekScrollBarUI());
        userScroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

        rightPanel.add(userHeader, BorderLayout.NORTH);
        rightPanel.add(userScroll, BorderLayout.CENTER);

        // Add proper panel
        // Layout Global:
        // WEST: Server List
        // CENTER: Main Split (Channel List | Chat | Users)

        // Main Container for Content (Channel + Chat + Users)
        JPanel contentContainer = new JPanel(new BorderLayout());
        contentContainer.add(leftPanel, BorderLayout.WEST);
        contentContainer.add(centerPanel, BorderLayout.CENTER);
        contentContainer.add(rightPanel, BorderLayout.EAST);

        // Server List Panel (Far Left)
        serverListPanel = new JPanel();
        serverListPanel.setLayout(new BoxLayout(serverListPanel, BoxLayout.Y_AXIS));
        serverListPanel.setBackground(new Color(32, 34, 37)); // Darker than dark
        // Remove fixed preferred size to allow dynamic height calculation
        // serverListPanel.setPreferredSize(new Dimension(72, 0));
        serverListPanel.setBorder(new EmptyBorder(10, 10, 10, 10)); // Padding for icons

        // Scroll for server list (in case of many servers)
        JScrollPane serverScroll = new JScrollPane(serverListPanel);
        serverScroll.setBorder(null);
        serverScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        serverScroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        serverScroll.getVerticalScrollBar().setUI(new ModernComponents.SleekScrollBarUI());
        serverScroll.getVerticalScrollBar().setPreferredSize(new Dimension(0, 0)); // Hidden scrollbar visual but
                                                                                   // functional

        // Fix width of the scroll pane region
        JPanel serverContainer = new JPanel(new BorderLayout());
        serverContainer.add(serverScroll, BorderLayout.CENTER);
        serverContainer.setPreferredSize(new Dimension(72, 0)); // Fixed width for sidebar

        add(serverContainer, BorderLayout.WEST);
        add(contentContainer, BorderLayout.CENTER);
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
        // Changement de canal
        channelList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                SidebarItem selectedItem = channelList.getSelectedValue();
                if (selectedItem == null)
                    return;

                // Handle Private Message (Friend) click -> open DM
                if (selectedItem instanceof PrivateMessageItem) {
                    PrivateMessageItem pmItem = (PrivateMessageItem) selectedItem;
                    openPrivateChat(pmItem.username);
                    return;
                }

                if (selectedItem.isChannel()) {
                    ChannelItem selected = (ChannelItem) selectedItem;
                    if (!selected.name.equals(currentChannel)) {
                        if (selected.type.equals("VOICE")) {
                            networkClient.sendCommand("/join " + selected.name);

                            if (voiceManager != null) {
                                voiceManager.joinChannel(selected.name, currentUser);
                                voiceManager.setTalkingListener((user, talking) -> {
                                    SwingUtilities.invokeLater(() -> updateTalkingStatus(user, talking));
                                });
                            }

                            voiceControlPanel.setVisible(true);
                            voiceStatusLabel.setText(selected.name);
                            centerLayout.show(centerPanel, "CHAT");
                            voiceUsersModel.clear();
                        } else {
                            centerLayout.show(centerPanel, "CHAT");
                            switchChannel(selected.name);
                        }
                    }
                }
            }
        });

        // Context Menu for Channels
        JPopupMenu channelMenu = new JPopupMenu();
        JMenuItem renameItem = new JMenuItem("Renommer");
        JMenuItem deleteChanItem = new JMenuItem("Supprimer");

        renameItem.addActionListener(e -> {
            SidebarItem selectedItem = channelList.getSelectedValue();
            if (selectedItem != null && selectedItem.isChannel()) {
                ChannelItem selected = (ChannelItem) selectedItem;
                String newName = JOptionPane.showInputDialog(this, "Nouveau nom pour #" + selected.name + ":");
                if (newName != null && !newName.trim().isEmpty()) {
                    networkClient.sendCommand("/renamechannel " + selected.name + " " + newName.trim());
                }
            }
        });

        deleteChanItem.addActionListener(e -> {
            SidebarItem selectedItem = channelList.getSelectedValue();
            if (selectedItem != null && selectedItem.isChannel()) {
                ChannelItem selected = (ChannelItem) selectedItem;
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
                    // Ne pas afficher le menu contextuel en mode Accueil
                    if (currentServer == null)
                        return;
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

        JButton loginBtn = new ModernComponents.ModernButton("Connexion");
        JButton registerBtn = new ModernComponents.ModernButton("S'inscrire", new Color(60, 63, 68),
                new Color(90, 93, 98));

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
                StyleConstants.setFontFamily(userStyle, "Segoe UI Emoji");
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
                StyleConstants.setFontFamily(textStyle, "Segoe UI Emoji");
                StyleConstants.setFontSize(textStyle, 14);

                // Insertion
                if ("ROLES_LIST".equals(msg.getType().name())
                        || "SYSTEM".equals(msg.getType().name()) && "ROLES_LIST".equals(msg.getFileName())) {
                    // Hacky way to detect roles list if type enum not updated, but we updated it
                    // below in logic usually.
                    // Actually we sent it as SYSTEM type with "ROLES_LIST" as channel? No in
                    // ClientHandler we sent:
                    // new Message("System", rolesStr, "ROLES_LIST", Message.MessageType.SYSTEM) =>
                    // channel is ROLES_LIST
                    // Wait, constructor is (sender, content, channel, type)
                    // ClientHandler: new Message("System", rolesStr, "ROLES_LIST",
                    // Message.MessageType.SYSTEM)
                }

                if (msg.getType() == Message.MessageType.SYSTEM && "ROLES_LIST".equals(msg.getChannel())) {
                    updateRoles(msg.getContent().split(","));
                    return;
                }

                if (msg.getType() == Message.MessageType.SYSTEM) {
                    chatDoc.insertString(chatDoc.getLength(), "🔒 " + msg.getContent() + "\n\n", textStyle);
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

                        JButton downloadBtn = new ModernComponents.ModernButton("⬇ Télécharger le fichier");
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
            if (currentDMUser != null) {
                // Send as private message
                networkClient.sendCommand("/msg " + currentDMUser + " " + text);
                inputField.setText("");
            } else {
                Message msg = new Message(currentUser, text, currentChannel,
                        text.startsWith("/") ? Message.MessageType.SYSTEM : Message.MessageType.CHAT);
                networkClient.sendMessage(msg);
                inputField.setText("");
            }
        }
    }

    private void switchChannel(String newChannel) {
        currentDMUser = null; // Exit DM mode
        if (dmCallButton != null)
            dmCallButton.setVisible(false);
        // Détection du type de salon
        String type = "TEXT";

        for (int i = 0; i < channelModel.getSize(); i++) {
            SidebarItem item = channelModel.get(i);
            if (item.isChannel() && item.getName().equals(newChannel)) {
                type = ((ChannelItem) item).type;
                break;
            }
        }

        if (type.equals("VOICE")) {
            voiceManager.joinChannel(newChannel, currentUser);
            voiceManager.setTalkingListener((user, talking) -> {
                SwingUtilities.invokeLater(() -> updateTalkingStatus(user, talking));
            });
            // Switch to Voice View
            if (centerLayout != null && centerPanel != null) {
                System.out.println("DEBUG: Switching to VOICE view");
                centerLayout.show(centerPanel, "VOICE");
                centerPanel.revalidate();
                centerPanel.repaint();
            } else {
                System.out.println("DEBUG: centerLayout or centerPanel is null!");
            }
        } else {
            voiceManager.leaveChannel();
            // Switch to Chat View
            if (centerLayout != null && centerPanel != null) {
                centerLayout.show(centerPanel, "CHAT");
            }
        }

        currentChannel = newChannel;

        // Update Header
        if (channelLabel != null) {
            channelLabel.setText(" # " + currentChannel);
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

    private String currentDMUser = null; // Tracks who we're DMing

    private void openPrivateChat(String friendUsername) {
        // Switch to chat view
        centerLayout.show(centerPanel, "CHAT");

        currentDMUser = friendUsername;

        // Use consistent DM channel name (alphabetically sorted like server does)
        String user1 = currentUser.compareTo(friendUsername) < 0 ? currentUser : friendUsername;
        String user2 = currentUser.compareTo(friendUsername) < 0 ? friendUsername : currentUser;
        currentChannel = "DM:" + user1 + ":" + user2;

        // Update header
        if (channelLabel != null) {
            channelLabel.setText(" @ " + friendUsername);
        }

        // Show call button
        if (dmCallButton != null) {
            dmCallButton.setVisible(true);
        }

        // Clear chat and load history
        chatArea.setText("");
        addSystemMessage("Conversation privée avec " + friendUsername);

        // Load DM history from server
        networkClient.sendCommand("/dm_history " + friendUsername);
    }

    public void addSystemMessage(String text) {
        displayMessage(new Message("System", text, currentChannel, Message.MessageType.SYSTEM));
    }

    public void showNotification(String title, String message) {
        SwingUtilities.invokeLater(() -> {
            // Create toast notification
            JWindow toast = new JWindow();
            JPanel panel = new JPanel(new BorderLayout(10, 5));
            panel.setBackground(new Color(30, 33, 36));
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT, 1),
                    new EmptyBorder(12, 16, 12, 16)));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setForeground(Color.WHITE);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

            JLabel msgLabel = new JLabel(message.length() > 50 ? message.substring(0, 47) + "..." : message);
            msgLabel.setForeground(TEXT_GRAY);
            msgLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

            panel.add(titleLabel, BorderLayout.NORTH);
            panel.add(msgLabel, BorderLayout.CENTER);
            toast.getContentPane().add(panel);
            toast.pack();

            // Position bottom-right of screen
            Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
            toast.setLocation(screenSize.width - toast.getWidth() - 20, screenSize.height - toast.getHeight() - 60);
            toast.setAlwaysOnTop(true);
            toast.setVisible(true);

            // Play notification sound
            try {
                java.awt.Toolkit.getDefaultToolkit().beep();
            } catch (Exception ignored) {
            }

            // Auto-dismiss after 4 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException ignored) {
                }
                SwingUtilities.invokeLater(() -> toast.dispose());
            }).start();
        });
    }

    public void showIncomingCall(String callerUsername) {
        SwingUtilities.invokeLater(() -> {
            // Play sound
            java.awt.Toolkit.getDefaultToolkit().beep();

            // Show incoming call dialog
            JDialog callDialog = new JDialog(this, "Appel entrant", false);
            callDialog.setSize(350, 200);
            callDialog.setLocationRelativeTo(this);
            callDialog.setAlwaysOnTop(true);

            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBackground(new Color(30, 33, 36));
            panel.setBorder(new EmptyBorder(20, 30, 20, 30));

            JLabel callerLabel = new JLabel("📞 " + callerUsername + " vous appelle !");
            callerLabel.setForeground(Color.WHITE);
            callerLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            callerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
            btnPanel.setOpaque(false);

            JButton acceptBtn = new JButton("✅ Accepter");
            acceptBtn.setBackground(new Color(46, 204, 113));
            acceptBtn.setForeground(Color.WHITE);
            acceptBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            acceptBtn.setFocusPainted(false);
            acceptBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
            acceptBtn.addActionListener(e -> {
                callDialog.dispose();
                startVoiceCall(callerUsername);
            });

            JButton rejectBtn = new JButton("❌ Refuser");
            rejectBtn.setBackground(new Color(237, 66, 69));
            rejectBtn.setForeground(Color.WHITE);
            rejectBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            rejectBtn.setFocusPainted(false);
            rejectBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
            rejectBtn.addActionListener(e -> callDialog.dispose());

            btnPanel.add(acceptBtn);
            btnPanel.add(rejectBtn);

            panel.add(Box.createVerticalGlue());
            panel.add(callerLabel);
            panel.add(Box.createVerticalStrut(20));
            panel.add(btnPanel);
            panel.add(Box.createVerticalGlue());

            callDialog.getContentPane().add(panel);
            callDialog.setVisible(true);

            // Auto-dismiss after 30 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException ignored) {
                }
                SwingUtilities.invokeLater(() -> {
                    if (callDialog.isVisible())
                        callDialog.dispose();
                });
            }).start();
        });
    }

    private void startVoiceCall(String otherUser) {
        // Open DM chat with the user
        openPrivateChat(otherUser);

        // Create a voice channel name for this call
        String callChannel = "call_" + otherUser + "_" + currentUser;

        // Use VoiceManager to join
        if (voiceManager != null) {
            voiceManager.joinChannel(callChannel, currentUser);
            voiceManager.setTalkingListener((user, talking) -> {
                SwingUtilities.invokeLater(() -> updateTalkingStatus(user, talking));
            });
            voiceControlPanel.setVisible(true);
            voiceStatusLabel.setText("Appel avec " + otherUser);
        }

        addSystemMessage("Appel vocal avec " + otherUser + " en cours...");
    }

    private void updateMicLevel(Double level) {
        SwingUtilities.invokeLater(() -> {
            this.currentMicLevel = level.intValue();
            // System.out.println("UI Mic Update: " + this.currentMicLevel); // Debug
            if (voiceUsersList != null) {
                voiceUsersList.repaint();
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
    // Renderer personnalisé pour les salons (Channel) ET les utilisateurs
    // (VoiceUser)
    private class SidebarRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            SidebarItem item = (SidebarItem) value;

            if (item.isChannel()) {
                ChannelItem cItem = (ChannelItem) item;
                String prefix = cItem.type.equals("VOICE") ? "🔊 " : "# ";
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, prefix + cItem.name, index, isSelected, cellHasFocus);

                label.setBorder(new EmptyBorder(0, 10, 0, 10));

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
            } else if (item instanceof PrivateMessageItem) {
                // Private Message (Friend) in Home sidebar
                PrivateMessageItem pmItem = (PrivateMessageItem) item;

                JPanel panel = new JPanel(new BorderLayout(10, 0));
                panel.setBorder(new EmptyBorder(5, 10, 5, 10));

                if (isSelected) {
                    panel.setBackground(new Color(66, 70, 77));
                } else if (index == hoveredChannelIndex) {
                    panel.setBackground(HOVER_BG);
                } else {
                    panel.setBackground(BG_SIDEBAR);
                }

                JLabel avatarLabel = new JLabel(ModernComponents.generateAvatar(pmItem.username, 28));

                JLabel nameLabel = new JLabel(pmItem.username);
                nameLabel.setForeground(Color.WHITE);
                nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

                boolean online = "Online".equals(pmItem.status);
                JPanel statusDot = new JPanel();
                statusDot.setPreferredSize(new Dimension(10, 10));
                statusDot.setBackground(online ? new Color(46, 204, 113) : TEXT_GRAY);
                statusDot.setOpaque(true);

                JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
                leftPanel.setOpaque(false);
                leftPanel.add(avatarLabel);
                leftPanel.add(nameLabel);

                panel.add(leftPanel, BorderLayout.WEST);
                panel.add(statusDot, BorderLayout.EAST);
                return panel;
            } else {
                // Voice User
                VoiceUserItem vItem = (VoiceUserItem) item;

                JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
                panel.setBackground(BG_SIDEBAR);

                JLabel icon = new JLabel(ModernComponents.generateAvatar(item.getName(), 24));

                JLabel name = new JLabel(item.getName());
                name.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                name.setForeground(TEXT_GRAY);

                if (item.getName().equals(currentUser)) {
                    name.setForeground(ACCENT);
                    name.setFont(new Font("Segoe UI", Font.BOLD, 13));
                }

                if (vItem.isTalking) {
                    name.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(Color.GREEN, 1),
                            new EmptyBorder(0, 4, 0, 4)));
                } else {
                    name.setBorder(new EmptyBorder(1, 5, 1, 5));
                }

                panel.setBorder(new EmptyBorder(2, 30, 2, 0));

                panel.add(icon);
                panel.add(name);
                return panel;
            }
        }
    }

    public void updateServerList(String[] servers) {
        SwingUtilities.invokeLater(() -> {
            serverListPanel.removeAll();
            serverButtons.clear();

            // Home Button (Friends)
            homeButton = new ModernComponents.ServerButton("H"); // Fallback to H but we will override paint
            homeButton.setToolTipText("Accueil (Amis)");
            homeButton.setBackground(new Color(114, 137, 218)); // Discord Blurple
            homeButton.addActionListener(e -> switchToHome());
            serverListPanel.add(homeButton);

            // Custom Paint for Home Button to draw a House
            // We can't easily override paintComponent on an instance without anonymous
            // class
            // But we can check text in ModernComponents.ServerButton logic?
            // "H" will be displayed if we don't change it.
            // Let's rely on switchToHome which sets everything up.

            // Separator
            JSeparator sepHome = new JSeparator(SwingConstants.HORIZONTAL);
            sepHome.setMaximumSize(new Dimension(50, 2));
            sepHome.setForeground(new Color(60, 63, 68));
            serverListPanel.add(Box.createVerticalStrut(5));
            serverListPanel.add(sepHome);
            serverListPanel.add(Box.createVerticalStrut(5));

            for (String server : servers) {
                ModernComponents.ServerButton btn = new ModernComponents.ServerButton(server);
                btn.addActionListener(e -> switchServer(server));

                // Right click to delete
                btn.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        checkPopup(e);
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        checkPopup(e);
                    }

                    private void checkPopup(MouseEvent e) {
                        if ((e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e))
                                && !"Main Server".equals(server)) {
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem deleteItem = new JMenuItem("Supprimer le serveur");
                            deleteItem.addActionListener(ev -> {
                                int confirm = JOptionPane.showConfirmDialog(ChatController.this,
                                        "Supprimer le serveur " + server + " ?",
                                        "Confirmation", JOptionPane.YES_NO_OPTION);
                                if (confirm == JOptionPane.YES_OPTION) {
                                    networkClient.sendCommand("/deleteserver " + server);
                                }
                            });
                            menu.add(deleteItem);
                            menu.show(btn, e.getX(), e.getY());
                        }
                    }
                });

                serverListPanel.add(btn);
                serverListPanel.add(Box.createVerticalStrut(10));
                serverButtons.put(server, btn);
            }

            // Separator
            JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
            sep.setMaximumSize(new Dimension(50, 2));
            sep.setForeground(new Color(60, 63, 68));
            serverListPanel.add(sep);
            serverListPanel.add(Box.createVerticalStrut(10));

            // Add Server Button
            JButton addServerBtn = new ModernComponents.ServerButton("+");
            addServerBtn.setBackground(new Color(46, 204, 113));
            addServerBtn.setForeground(Color.WHITE);
            addServerBtn.setToolTipText("Créer un serveur");
            addServerBtn.addActionListener(e -> {
                String name = JOptionPane.showInputDialog(this, "Nom du nouveau serveur :");
                if (name != null && !name.trim().isEmpty()) {
                    networkClient.sendMessage(
                            new Message(currentUser, name.trim(), "system", Message.MessageType.CREATE_SERVER));
                }
            });
            serverListPanel.add(addServerBtn);

            serverListPanel.revalidate();
            serverListPanel.repaint();

            // Select current server if exists, else fallback
            if (!serverButtons.containsKey(currentServer)) {
                if (serverButtons.containsKey("Main Server")) {
                    currentServer = "Main Server";
                } else if (!serverButtons.isEmpty()) {
                    currentServer = serverButtons.keySet().iterator().next();
                }
            }
            switchServer(currentServer);
        });
    }

    private void switchServer(String serverName) {
        // Validation with fallback
        if (!serverButtons.containsKey(serverName)) {
            if (serverButtons.containsKey("Main Server")) {
                serverName = "Main Server";
            } else if (!serverButtons.isEmpty()) {
                serverName = serverButtons.keySet().iterator().next();
            }
        }

        this.currentServer = serverName;
        if (serverHeader != null) {
            serverHeader.setText("  " + serverName.toUpperCase());
        }

        // Update Buttons Visuals
        for (Map.Entry<String, ModernComponents.ServerButton> entry : serverButtons.entrySet()) {
            entry.getValue().setSelected(entry.getKey().equals(serverName));
        }

        // Show buttons again
        if (addChannelBtn != null)
            addChannelBtn.setVisible(true);
        if (settingsBtn != null)
            settingsBtn.setVisible(true);

        // Update Channels
        channelModel.clear();
        java.util.List<ChannelItem> channels = serverChannels.get(serverName);
        if (channels != null) {
            // Sort? Text first then Voice?
            for (ChannelItem item : channels) {
                channelModel.addElement(item);
            }
        }

        // If current channel is NOT in the new server, switch to a default channel of
        // that server
        boolean currentInServer = false;
        if (channels != null) {
            for (ChannelItem item : channels) {
                if (item.name.equals(currentChannel)) {
                    currentInServer = true;
                    break;
                }
            }
        }

        if (!currentInServer && !channelModel.isEmpty()) {
            // Switch to first channel
            SidebarItem first = channelModel.get(0);
            if (first.isChannel()) {
                switchChannel(((ChannelItem) first).name);
            }
        }
    }

    public void updateChannelList(String[] channelsData) {
        SwingUtilities.invokeLater(() -> {
            serverChannels.clear();

            for (String s : channelsData) {
                // Format: name:type:serverName
                String[] parts = s.split(":");
                if (parts.length >= 2) {
                    String name = parts[0];
                    String type = parts[1];
                    String server = parts.length > 2 ? parts[2] : "Main Server";

                    serverChannels.computeIfAbsent(server, k -> new java.util.ArrayList<>())
                            .add(new ChannelItem(name, type));
                }
            }

            // Refresh current view
            switchServer(currentServer);
        });
    }

    public void updateVoiceUsers(String channelName, String[] users) {
        SwingUtilities.invokeLater(() -> {
            // Update Sidebar for this channel
            updateSidebarVoiceUsers(channelName, users);

            // Update user list in the Voice Panel only if it matches our current voice
            // session
            String currentVoice = voiceStatusLabel.getText();
            if (currentVoice != null && currentVoice.equals(channelName)) {
                voiceUsersModel.clear();
                for (String user : users) {
                    voiceUsersModel.addElement(user);
                }
            }
        });
    }

    private void updateTalkingStatus(String user, boolean talking) {
        talkingStates.put(user, talking);
        if (voiceUsersList != null) {
            voiceUsersList.repaint();
        }

        for (int i = 0; i < channelModel.getSize(); i++) {
            SidebarItem item = channelModel.get(i);
            if (!item.isChannel() && item.getName().equals(user)) {
                VoiceUserItem vItem = (VoiceUserItem) item;
                vItem.setTalking(talking);
                channelList.repaint(channelList.getCellBounds(i, i));
                break;
            }
        }
    }

    private void leaveVoiceChannel() {
        // Clear users from sidebar for the current voice channel
        String currentVoice = voiceStatusLabel.getText();
        if (currentVoice != null && !currentVoice.isEmpty()) {
            updateSidebarVoiceUsers(currentVoice, new String[0]);
        }

        // Leave voice logic
        voiceManager.leaveChannel();
        voiceControlPanel.setVisible(false);

        // Restore presence to current text channel
        networkClient.sendCommand("/join " + currentChannel);
    }

    private void updateSidebarVoiceUsers(String channelName, String[] users) {
        // Find channel index
        int channelIndex = -1;
        boolean isVoice = false;

        for (int i = 0; i < channelModel.getSize(); i++) {
            SidebarItem item = channelModel.get(i);
            if (item.isChannel() && item.getName().equals(channelName)) {
                channelIndex = i;
                if (item instanceof ChannelItem) {
                    isVoice = "VOICE".equals(((ChannelItem) item).type);
                }
                break;
            }
        }

        if (channelIndex != -1) {
            // Remove existing users under this channel
            int nextIndex = channelIndex + 1;
            while (nextIndex < channelModel.getSize()) {
                if (channelModel.get(nextIndex).isChannel()) {
                    break;
                }
                channelModel.remove(nextIndex);
            }

            // Insert new users ONLY if it is a voice channel
            if (isVoice) {
                int index = 0;
                for (String user : users) {
                    channelModel.add(channelIndex + 1 + index, new VoiceUserItem(user));
                    index++;
                }
            }
        }
    }

    private void switchToHome() {
        centerLayout.show(centerPanel, "FRIENDS");
        currentServer = null;

        for (ModernComponents.ServerButton b : serverButtons.values())
            b.setSelected(false);
        if (homeButton != null)
            homeButton.setSelected(true);

        serverHeader.setText("  CONVERSATIONS");

        // Populate Sidebar with Friends (as pseudo-history of DMs)
        channelModel.clear();
        if (friendsModel != null && !friendsModel.isEmpty()) {
            for (int i = 0; i < friendsModel.size(); i++) {
                String f = friendsModel.get(i);
                String[] parts = f.split(":");
                channelModel.addElement(new PrivateMessageItem(parts[0], parts.length > 1 ? parts[1] : "Offline"));
            }
        }

        networkClient.sendCommand("/friend list");
        networkClient.sendCommand("/myid");

        // Hide server management buttons
        if (addChannelBtn != null)
            addChannelBtn.setVisible(false);
        if (settingsBtn != null)
            settingsBtn.setVisible(false);
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

        JButton createBtn = new ModernComponents.ModernButton("Créer");
        createBtn.setPreferredSize(new Dimension(100, 35));
        createBtn.addActionListener(e -> {
            String name = nameField.getText().trim().replace(" ", "-").toLowerCase();
            if (!name.isEmpty()) {
                String type = voiceRadio.isSelected() ? "VOICE" : "TEXT";
                // Handle spaces in server name? Server name is last argument.
                // ClientHandler needs to be updated to join remaining parts or use a delimiter.
                // Let's use a safe delimiter if possible, or assume simple parsing for now.
                // Or better, send as Message object with custom fields? No, command is string
                // based.
                // Let's just append it.
                networkClient.sendCommand("/create " + name + " " + type + " " + currentServer);
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
        JPopupMenu emojiMenu = new JPopupMenu();
        emojiMenu.setBackground(new Color(47, 49, 54));
        emojiMenu.setBorder(BorderFactory.createLineBorder(new Color(32, 34, 37), 1));

        JPanel content = new JPanel(new GridLayout(5, 6, 2, 2));
        content.setBackground(new Color(47, 49, 54));
        content.setBorder(new EmptyBorder(5, 5, 5, 5));

        String[] emojis = {
                "😀", "😃", "😄", "😁", "😆", "😅",
                "😂", "🤣", "😊", "😇", "🙂", "🙃",
                "😉", "😌", "😍", "🥰", "😘", "😗",
                "👍", "👎", "👌", "✌️", "🤞", "🤟",
                "❤️", "🧡", "💛", "💚", "💙", "💜"
        };

        for (String emoji : emojis) {
            String hex = Integer.toHexString(emoji.codePointAt(0));
            String url = "https://raw.githubusercontent.com/twitter/twemoji/master/assets/72x72/" + hex + ".png";

            JButton btn = new JButton(emoji);
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 24));

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
                }
            }).start();

            btn.setForeground(Color.WHITE);
            btn.setBackground(new Color(47, 49, 54));
            btn.setOpaque(true);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(40, 40));

            btn.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    btn.setBackground(new Color(88, 101, 242));
                }

                public void mouseExited(MouseEvent e) {
                    btn.setBackground(new Color(47, 49, 54));
                }
            });

            final String emojiChar = emoji;
            btn.addActionListener(e -> {
                SwingUtilities.invokeLater(() -> {
                    inputField.setText(inputField.getText() + emojiChar);
                    inputField.requestFocusInWindow();
                    inputField.setCaretPosition(inputField.getText().length());
                });
                emojiMenu.setVisible(false);
            });
            content.add(btn);
        }

        emojiMenu.add(content);
        emojiMenu.show(invoker, 0, -250);
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

            // Indicateur vert (Status)
            JPanel indicator = new JPanel();
            indicator.setPreferredSize(new Dimension(10, 10));

            // Determine color based on status
            String status = userStatuses.getOrDefault(value.toString(), "ONLINE");
            Color statusColor = new Color(46, 204, 113); // Default Green

            if ("IDLE".equals(status))
                statusColor = new Color(250, 166, 26); // Orange
            else if ("DND".equals(status))
                statusColor = new Color(237, 66, 69); // Red

            indicator.setBackground(statusColor);
            // Petit contour pour que ça ressorte
            indicator.setBorder(null);

            // Shape ronde pour l'indicateur (hacky via paintComponent si on voulait, mais
            // JPanel carré est ok pour l'instant
            // ou on peut faire un JPanel custom)

            // Avatar
            JLabel icon = new JLabel(ModernComponents.generateAvatar(value.toString(), 32));

            JLabel name = new JLabel(value.toString());
            name.setForeground(isSelected ? Color.WHITE : (index == hoveredUserIndex ? TEXT_NORMAL : TEXT_GRAY));
            name.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            panel.add(indicator);
            panel.add(icon); // Add Avatar
            panel.add(name);
            return panel;
        }
    }

    private class VoiceUserRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {

            JPanel panel = new JPanel(new BorderLayout(10, 0));
            panel.setBorder(new EmptyBorder(8, 10, 8, 10));

            if (isSelected) {
                panel.setBackground(new Color(66, 70, 77));
            } else {
                panel.setBackground(BG_DARK);
            }

            // Indicator (Left) replaced by Avatar with border in this design?
            // Let's keep indicator for "speaking" visualization on the avatar itself?
            // For now, simple avatar

            JPanel leftPanel = new JPanel(new GridBagLayout());
            leftPanel.setOpaque(false);

            JLabel avatar = new JLabel(ModernComponents.generateAvatar(value.toString(), 40));
            leftPanel.add(avatar);

            panel.add(leftPanel, BorderLayout.WEST);

            // Green Border if talking
            if (talkingStates.getOrDefault(value.toString(), false)) {
                panel.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(46, 204, 113), 2),
                        new EmptyBorder(6, 8, 6, 8))); // Adjusted inner padding
            }

            // Text Panel (Center)
            JPanel textPanel = new JPanel();
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            textPanel.setOpaque(false);

            String username = value.toString();
            JLabel nameLabel = new JLabel(username);
            nameLabel.setForeground(isSelected ? Color.WHITE : TEXT_NORMAL);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            textPanel.add(nameLabel);

            // Mic Level Bar (Only for current user)
            if (username.equals(currentUser)) {
                JProgressBar bar = new JProgressBar(0, 100);
                bar.setValue(currentMicLevel);
                bar.setPreferredSize(new Dimension(100, 6)); // Slightly thicker
                bar.setMaximumSize(new Dimension(200, 6));

                // Pulsing Color Logic for "High" sound appearance
                Color barColor = new Color(46, 204, 113); // Default Green
                if (currentMicLevel > 25) { // Threshold for oscillation
                    long time = System.currentTimeMillis();
                    // Fast oscillation (every 100ms)
                    if ((time / 100) % 2 == 0) {
                        barColor = new Color(30, 130, 76); // Darker Green (Sombre)
                    } else {
                        barColor = new Color(46, 204, 113); // Standard Green
                    }
                }

                bar.setForeground(barColor);
                bar.setBackground(new Color(32, 34, 37));
                bar.setBorderPainted(false);
                // Force Basic UI to respect custom colors on Windows
                bar.setUI(new javax.swing.plaf.basic.BasicProgressBarUI());
                bar.setAlignmentX(Component.LEFT_ALIGNMENT);

                textPanel.add(Box.createVerticalStrut(6));
                textPanel.add(bar);
            }

            panel.add(textPanel, BorderLayout.CENTER);
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
        // Use cached roles or default
        java.util.List<String> possibleRoles = (cachedRoles != null && !cachedRoles.isEmpty()) ? cachedRoles
                : java.util.Arrays.asList("Admin", "Membre");

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

        if (cachedRoles == null || cachedRoles.isEmpty()) {
            // Request update for next time
            networkClient.sendCommand("/getroles");
        }
        menu.show(userList, x, y);
    }

    // --- INTERFACE GESTION ROLES ---
    private void showRoleManager() {
        JDialog roleDialog = new JDialog(this, "Gestion des Rôles", true);
        roleDialog.setSize(500, 400);
        roleDialog.setLocationRelativeTo(this);
        roleDialog.setLayout(new BorderLayout());
        roleDialog.getContentPane().setBackground(BG_DARK);

        JTabbedPane tabs = new JTabbedPane();

        // --- Tab 1: Créer / Modifier ---
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

        JButton saveBtn = new ModernComponents.ModernButton("Créer / Mettre à jour");
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

        tabs.addTab("Créer", formPanel);

        // --- Tab 2: Liste / Supprimer ---
        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBackground(BG_DARK);

        // Initialize or clear model
        if (roleListModel == null) {
            roleListModel = new DefaultListModel<>();
        }
        roleListModel.clear();

        if (cachedRoles != null) {
            for (String r : cachedRoles) {
                if (!r.equalsIgnoreCase("Admin")) { // Masquer le rôle Admin
                    roleListModel.addElement(r);
                }
            }
        }

        // Request fresh list
        networkClient.sendCommand("/getroles");

        JList<String> roleList = new JList<>(roleListModel);
        roleList.setBackground(BG_SIDEBAR);
        roleList.setForeground(TEXT_NORMAL);

        JButton deleteBtn = new ModernComponents.ModernButton("Supprimer le rôle");
        deleteBtn.setBackground(new Color(237, 66, 69));
        deleteBtn.addActionListener(e -> {
            String selected = roleList.getSelectedValue();
            if (selected != null) {
                networkClient.sendCommand("/deleterole " + selected);
                roleListModel.removeElement(selected);
                // Also remove locally
                if (cachedRoles != null)
                    cachedRoles.remove(selected);
            }
        });

        listPanel.add(new JScrollPane(roleList), BorderLayout.CENTER);
        listPanel.add(deleteBtn, BorderLayout.SOUTH);

        tabs.addTab("Liste", listPanel);

        roleDialog.add(tabs, BorderLayout.CENTER);
        roleDialog.add(saveBtn, BorderLayout.SOUTH);
        roleDialog.setVisible(true);
    }

    private java.util.List<String> cachedRoles = new java.util.ArrayList<>();

    public void updateRoles(String[] roles) {
        cachedRoles.clear();
        for (String r : roles)
            cachedRoles.add(r);

        // Update the UI model if it exists
        if (roleListModel != null) {
            roleListModel.clear();
            for (String r : roles) {
                if (!r.equalsIgnoreCase("Admin")) { // Masquer le rôle Admin
                    roleListModel.addElement(r);
                }
            }
        }
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