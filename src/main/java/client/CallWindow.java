package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Expanded call window for voice calls (DM or voice channels).
 * Features: Mute, Deafen, Screen Share, Hang Up, Mic Level indicator.
 */
public class CallWindow extends JFrame {

    private static final Color BG_DARK = new Color(30, 33, 36);
    private static final Color BG_PANEL = new Color(47, 49, 54);
    private static final Color ACCENT = new Color(88, 101, 242);
    private static final Color RED = new Color(237, 66, 69);
    private static final Color GREEN = new Color(46, 204, 113);
    private static final Color TEXT_WHITE = new Color(220, 221, 222);
    private static final Color TEXT_GRAY = new Color(142, 146, 151);

    private VoiceManager voiceManager;
    private ChatController controller;
    private String channelName;

    private JButton muteBtn;
    private JButton deafenBtn;
    private JButton screenShareBtn;
    private JButton hangUpBtn;
    private JPanel micLevelBar;
    private JLabel statusLabel;
    private JLabel timerLabel;
    private JPanel screenPanel;

    private boolean isScreenSharing = false;
    private Thread screenShareThread;
    private long callStartTime;

    public CallWindow(VoiceManager voiceManager, ChatController controller, String channelName) {
        super("Appel - " + channelName);
        this.voiceManager = voiceManager;
        this.controller = controller;
        this.channelName = channelName;
        this.callStartTime = System.currentTimeMillis();

        setSize(700, 500);
        setMinimumSize(new Dimension(500, 400));
        setLocationRelativeTo(controller);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());

        buildUI();
        startTimer();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopScreenShare();
            }
        });

        setVisible(true);
    }

    private void buildUI() {
        // === Top Bar ===
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(BG_PANEL);
        topBar.setBorder(new EmptyBorder(10, 20, 10, 20));
        topBar.setPreferredSize(new Dimension(0, 50));

        statusLabel = new JLabel("🔊 " + channelName);
        statusLabel.setForeground(TEXT_WHITE);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));

        timerLabel = new JLabel("00:00");
        timerLabel.setForeground(GREEN);
        timerLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        topBar.add(statusLabel, BorderLayout.WEST);
        topBar.add(timerLabel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // === Center: Screen Share Area ===
        screenPanel = new JPanel(new BorderLayout());
        screenPanel.setBackground(BG_DARK);

        JLabel placeholderLabel = new JLabel("Activez le partage d'écran pour diffuser votre écran",
                SwingConstants.CENTER);
        placeholderLabel.setForeground(TEXT_GRAY);
        placeholderLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        screenPanel.add(placeholderLabel, BorderLayout.CENTER);

        add(screenPanel, BorderLayout.CENTER);

        // === Bottom Control Bar ===
        JPanel bottomBar = new JPanel();
        bottomBar.setLayout(new BoxLayout(bottomBar, BoxLayout.Y_AXIS));
        bottomBar.setBackground(BG_PANEL);
        bottomBar.setBorder(new EmptyBorder(10, 20, 15, 20));

        // Mic Level bar
        JPanel micLevelContainer = new JPanel(new BorderLayout());
        micLevelContainer.setBackground(BG_PANEL);
        micLevelContainer.setBorder(new EmptyBorder(0, 0, 10, 0));
        micLevelContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 12));

        JPanel micLevelBg = new JPanel(new BorderLayout());
        micLevelBg.setBackground(new Color(64, 68, 75));
        micLevelBg.setPreferredSize(new Dimension(0, 6));

        micLevelBar = new JPanel();
        micLevelBar.setBackground(GREEN);
        micLevelBar.setPreferredSize(new Dimension(0, 6));
        micLevelBg.add(micLevelBar, BorderLayout.WEST);
        micLevelContainer.add(micLevelBg, BorderLayout.CENTER);

        JLabel micLabel = new JLabel("Micro  ");
        micLabel.setForeground(TEXT_GRAY);
        micLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        micLevelContainer.add(micLabel, BorderLayout.WEST);

        bottomBar.add(micLevelContainer);

        // Buttons row
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnRow.setOpaque(false);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        muteBtn = createControlButton("🎙 Muet", false);
        muteBtn.addActionListener(e -> toggleMute());

        deafenBtn = createControlButton("🔇 Sourdine", false);
        deafenBtn.addActionListener(e -> toggleDeafen());

        screenShareBtn = createControlButton("🖥 Partager", false);
        screenShareBtn.addActionListener(e -> toggleScreenShare());

        hangUpBtn = createControlButton("📞 Raccrocher", true);
        hangUpBtn.setBackground(RED);
        hangUpBtn.addActionListener(e -> hangUp());

        btnRow.add(muteBtn);
        btnRow.add(deafenBtn);
        btnRow.add(screenShareBtn);
        btnRow.add(hangUpBtn);

        bottomBar.add(btnRow);
        add(bottomBar, BorderLayout.SOUTH);

        // Register mic level listener
        if (voiceManager != null) {
            voiceManager.setLevelListener(level -> {
                SwingUtilities.invokeLater(() -> updateMicLevel(level));
            });
        }
    }

    private JButton createControlButton(String text, boolean isDanger) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(Color.WHITE);
        btn.setBackground(isDanger ? RED : new Color(64, 68, 75));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false); // Let us control the painting
        btn.setOpaque(false);
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(140, 40));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isDanger) {
                    btn.setBackground(ACCENT);
                    btn.repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // Reset to current state color
                if (!isDanger) {
                    btn.setBackground(new Color(64, 68, 75));
                    btn.repaint();
                }
            }
        });

        return btn;
    }

    private void toggleMute() {
        if (voiceManager == null)
            return;
        boolean muted = !voiceManager.isMuted();
        voiceManager.setMuted(muted);
        muteBtn.setText(muted ? "🎙 Unmute" : "🎙 Muet");
        muteBtn.setBackground(muted ? RED : new Color(64, 68, 75));
        statusLabel.setText(muted ? "🔇 Micro coupé - " + channelName : "🔊 " + channelName);
    }

    private void toggleDeafen() {
        if (voiceManager == null)
            return;
        boolean deafened = !voiceManager.isDeafened();
        voiceManager.setDeafened(deafened);
        deafenBtn.setText(deafened ? "🔇 Activer" : "🔇 Sourdine");
        deafenBtn.setBackground(deafened ? RED : new Color(64, 68, 75));
    }

    private void toggleScreenShare() {
        if (isScreenSharing) {
            stopScreenShare();
        } else {
            startScreenShare();
        }
    }

    private void startScreenShare() {
        isScreenSharing = true;
        screenShareBtn.setText("🖥 Arrêter");
        screenShareBtn.setBackground(RED);

        // Replace center content with live screen preview
        screenPanel.removeAll();

        JLabel screenView = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getIcon() != null) {
                    Image img = ((ImageIcon) getIcon()).getImage();
                    // Scale to fit
                    int w = getWidth();
                    int h = getHeight();
                    double ratio = Math.min((double) w / img.getWidth(null), (double) h / img.getHeight(null));
                    int newW = (int) (img.getWidth(null) * ratio);
                    int newH = (int) (img.getHeight(null) * ratio);
                    int x = (w - newW) / 2;
                    int y = (h - newH) / 2;
                    g.setColor(BG_DARK);
                    g.fillRect(0, 0, w, h);
                    g.drawImage(img, x, y, newW, newH, null);
                }
            }
        };
        screenView.setHorizontalAlignment(SwingConstants.CENTER);
        screenPanel.add(screenView, BorderLayout.CENTER);
        screenPanel.revalidate();
        screenPanel.repaint();

        screenShareThread = new Thread(() -> {
            try {
                Robot robot = new Robot();
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                Rectangle screenRect = new Rectangle(screenSize);

                while (isScreenSharing) {
                    BufferedImage capture = robot.createScreenCapture(screenRect);
                    // Scale down for display
                    ImageIcon icon = new ImageIcon(capture);
                    SwingUtilities.invokeLater(() -> {
                        screenView.setIcon(icon);
                        screenView.repaint();
                    });
                    Thread.sleep(500); // 2 FPS for preview
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "ScreenShare-Capture");
        screenShareThread.setDaemon(true);
        screenShareThread.start();
    }

    private void stopScreenShare() {
        isScreenSharing = false;
        screenShareBtn.setText("🖥 Partager");
        screenShareBtn.setBackground(new Color(64, 68, 75));

        screenPanel.removeAll();
        JLabel placeholderLabel = new JLabel("Activez le partage d'écran pour diffuser votre écran",
                SwingConstants.CENTER);
        placeholderLabel.setForeground(TEXT_GRAY);
        placeholderLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        screenPanel.add(placeholderLabel, BorderLayout.CENTER);
        screenPanel.revalidate();
        screenPanel.repaint();
    }

    private void hangUp() {
        stopScreenShare();
        if (voiceManager != null) {
            voiceManager.leaveChannel();
        }
        controller.onCallWindowHangUp();
        dispose();
    }

    private void updateMicLevel(double level) {
        int width = (int) (screenPanel.getWidth() * (level / 100.0));
        micLevelBar.setPreferredSize(new Dimension(Math.max(0, width), 6));
        micLevelBar.revalidate();
    }

    private void startTimer() {
        Thread timerThread = new Thread(() -> {
            while (isDisplayable()) {
                long elapsed = (System.currentTimeMillis() - callStartTime) / 1000;
                long minutes = elapsed / 60;
                long seconds = elapsed % 60;
                String time = String.format("%02d:%02d", minutes, seconds);
                SwingUtilities.invokeLater(() -> timerLabel.setText(time));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Call-Timer");
        timerThread.setDaemon(true);
        timerThread.start();
    }
}
