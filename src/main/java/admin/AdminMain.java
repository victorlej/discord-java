package admin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import server.DatabaseManager;

public class AdminMain extends JFrame {

    private static final String PASSWORD = "Azerty1234";
    // Theme Colors
    private static final Color BG_DARK = new Color(54, 57, 63);
    private static final Color BG_SIDEBAR = new Color(47, 49, 54);
    private static final Color BG_INPUT = new Color(64, 68, 75);
    private static final Color ACCENT = new Color(88, 101, 242);
    private static final Color TEXT_NORMAL = new Color(220, 221, 222);
    private static final Color TEXT_HEADER = new Color(142, 146, 151);

    public static void main(String[] args) {
        try {
            Class.forName("org.sqlite.JDBC");
            // Also need SLF4J if strictly required, but it's a runtime dep usually.
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> new AdminMain().showLogin());
    }

    public AdminMain() {
        setTitle("Admin Console - Discord Java");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(BG_DARK);
    }

    private void showLogin() {
        JDialog loginDialog = new JDialog((Frame) null, "Authentification Admin", true);
        loginDialog.setUndecorated(true);
        loginDialog.setSize(350, 220);
        loginDialog.setLocationRelativeTo(null);
        loginDialog.getRootPane().setBorder(BorderFactory.createLineBorder(ACCENT, 2));

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_DARK);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));

        JLabel title = new JLabel("ADMIN ACCESS");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel label = new JLabel("Mot de passe requis");
        label.setForeground(TEXT_HEADER);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPasswordField passField = new JPasswordField();
        passField.setBackground(BG_INPUT);
        passField.setForeground(Color.WHITE);
        passField.setCaretColor(Color.WHITE);
        passField.setBorder(new EmptyBorder(5, 10, 5, 10));
        passField.setMaximumSize(new Dimension(300, 35));
        passField.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton btn = new JButton("Connexion");
        styleButton(btn, ACCENT);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(150, 40));

        JButton closeBtn = new JButton("X");
        closeBtn.setForeground(TEXT_HEADER);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> System.exit(0));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_DARK);
        header.add(closeBtn, BorderLayout.EAST);
        header.setMaximumSize(new Dimension(400, 20));
        header.setAlignmentX(Component.CENTER_ALIGNMENT);

        btn.addActionListener(e -> {
            String p = new String(passField.getPassword());
            if (PASSWORD.equals(p)) {
                loginDialog.dispose();
                initUI();
                setVisible(true);
            } else {
                JOptionPane.showMessageDialog(loginDialog, "Mot de passe incorrect", "Erreur",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        panel.add(header);
        panel.add(Box.createVerticalStrut(10));
        panel.add(title);
        panel.add(Box.createVerticalStrut(5));
        panel.add(label);
        panel.add(Box.createVerticalStrut(20));
        panel.add(passField);
        panel.add(Box.createVerticalStrut(20));
        panel.add(btn);

        loginDialog.add(panel);
        loginDialog.setVisible(true);
    }

    private void initUI() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_SIDEBAR);
        tabs.setForeground(Color.WHITE);

        // Custom styling for tabs isn't trivial in pure Swing without UI Manager hacks,
        // but let's keep it simple.

        tabs.addTab("Utilisateurs", createUsersPanel());
        tabs.addTab("Salons", createChannelsPanel());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(BG_DARK);

        JLabel header = new JLabel("  CONSOLE D'ADMINISTRATION");
        header.setFont(new Font("Segoe UI", Font.BOLD, 24));
        header.setForeground(Color.WHITE);
        header.setBorder(new EmptyBorder(10, 0, 10, 0));

        mainPanel.add(header, BorderLayout.NORTH);
        mainPanel.add(tabs, BorderLayout.CENTER);

        add(mainPanel);
    }

    private JPanel createUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);

        String[] columns = { "Utilisateur", "Bloqué ?", "Admin (Création Salons)?" };
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable table = new JTable(model);
        styleTable(table);

        JButton refreshBtn = new JButton("Rafraîchir");
        JButton blockBtn = new JButton("Changer Bloqué");
        JButton adminBtn = new JButton("Changer Admin");
        JButton deleteBtn = new JButton("Supprimer");

        styleButton(refreshBtn, new Color(74, 98, 100));
        styleButton(blockBtn, new Color(192, 57, 43));
        styleButton(adminBtn, new Color(243, 156, 18));
        styleButton(deleteBtn, Color.RED);

        JPanel tools = new JPanel();
        tools.setBackground(BG_DARK);
        tools.add(refreshBtn);
        tools.add(blockBtn);
        tools.add(adminBtn);
        tools.add(deleteBtn);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(tools, BorderLayout.SOUTH);

        refreshBtn.addActionListener(e -> loadUsers(model));

        blockBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                String user = (String) model.getValueAt(row, 0);
                boolean current = (boolean) model.getValueAt(row, 1); // Careful with type
                DatabaseManager.blockUser(user, !current);
                loadUsers(model);
            }
        });

        adminBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                String user = (String) model.getValueAt(row, 0);
                boolean current = (boolean) model.getValueAt(row, 2);
                DatabaseManager.setCanCreateChannel(user, !current);
                loadUsers(model);
            }
        });

        deleteBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                String user = (String) model.getValueAt(row, 0);
                int confirm = JOptionPane.showConfirmDialog(this, "Supprimer " + user + " ?", "Confirmation",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    DatabaseManager.deleteUser(user);
                    loadUsers(model);
                }
            }
        });

        loadUsers(model);
        return panel;
    }

    private void loadUsers(DefaultTableModel model) {
        model.setRowCount(0);
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:discord.db");
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT username, blocked, can_create_channel FROM users")) {

            while (rs.next()) {
                model.addRow(new Object[] {
                        rs.getString("username"),
                        rs.getBoolean("blocked"),
                        rs.getBoolean("can_create_channel")
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JPanel createChannelsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_DARK);

        String[] columns = { "Nom", "Type" };
        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable table = new JTable(model);
        styleTable(table);

        JButton refreshBtn = new JButton("Rafraîchir");
        JButton deleteBtn = new JButton("Supprimer");

        styleButton(refreshBtn, new Color(74, 98, 100));
        styleButton(deleteBtn, Color.RED);

        JPanel tools = new JPanel();
        tools.setBackground(BG_DARK);
        tools.add(refreshBtn);
        tools.add(deleteBtn);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(tools, BorderLayout.SOUTH);

        refreshBtn.addActionListener(e -> loadChannels(model));
        deleteBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row != -1) {
                String name = (String) model.getValueAt(row, 0);
                int confirm = JOptionPane.showConfirmDialog(this, "Supprimer le salon #" + name + " ?", "Confirmation",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    DatabaseManager.deleteChannel(name);
                    loadChannels(model);
                }
            }
        });

        loadChannels(model);
        return panel;
    }

    private void loadChannels(DefaultTableModel model) {
        model.setRowCount(0);
        for (DatabaseManager.ChannelData cd : DatabaseManager.getChannels()) {
            model.addRow(new Object[] { cd.name, cd.type });
        }
    }

    private void styleTable(JTable table) {
        table.setBackground(BG_SIDEBAR);
        table.setForeground(TEXT_NORMAL);
        table.setGridColor(BG_DARK);
        table.setSelectionBackground(ACCENT);
        table.setSelectionForeground(Color.WHITE);
        table.setRowHeight(30);
        table.getTableHeader().setBackground(BG_DARK);
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));

        // Custom renderer for boolean columns if needed, but default is usually
        // Checkbox or string
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }
}
