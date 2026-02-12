package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public class ModernComponents {

    public static final Color ACCENT = new Color(88, 101, 242); // #5865f2
    public static final Color ACCENT_HOVER = new Color(71, 82, 196); // Darker
    public static final Color BG_DARK = new Color(54, 57, 63); // #36393f
    public static final Color BG_DARKER = new Color(47, 49, 54); // #2f3136
    public static final Color BG_INPUT = new Color(64, 68, 75); // #40444b
    public static final Color TEXT_NORMAL = new Color(220, 221, 222);

    /**
     * Génère un avatar rond avec les initiales et une couleur de fond unique basée
     * sur le pseudo.
     */
    public static ImageIcon generateAvatar(String username, int size) {
        int hash = username.hashCode();
        // Palette de couleurs "Discord-like" pour les avatars
        Color[] colors = {
                new Color(218, 55, 60), // Red
                new Color(88, 101, 242), // Blue
                new Color(87, 242, 135), // Green
                new Color(254, 231, 92), // Yellow
                new Color(235, 69, 158) // Pink
        };
        Color bg = colors[Math.abs(hash) % colors.length];

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fond rond
        g2.setColor(bg);
        g2.fillOval(0, 0, size, size);

        // Initiales
        String initial = username.length() > 0 ? username.substring(0, 1).toUpperCase() : "?";
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Segoe UI", Font.BOLD, size / 2));

        FontMetrics fm = g2.getFontMetrics();
        int x = (size - fm.stringWidth(initial)) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();

        g2.drawString(initial, x, y);
        g2.dispose();

        return new ImageIcon(img);
    }

    /**
     * Bouton moderne avec coins arrondis et effet hover.
     */
    public static class ModernButton extends JButton {
        private Color normalColor;
        private Color hoverColor;

        public ModernButton(String text) {
            this(text, ACCENT, ACCENT_HOVER);
        }

        public ModernButton(String text, Color normal, Color hover) {
            super(text);
            this.normalColor = normal;
            this.hoverColor = hover;

            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    setBackground(hoverColor);
                }

                public void mouseExited(MouseEvent e) {
                    setBackground(normalColor);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (getModel().isPressed()) {
                g2.setColor(hoverColor.darker());
            } else if (getModel().isRollover()) {
                g2.setColor(hoverColor);
            } else {
                g2.setColor(normalColor);
            }

            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            super.paintComponent(g);
            g2.dispose();
        }
    }

    /**
     * Icone de serveur ronde
     */
    /**
     * Icone de serveur ronde
     */
    public static class ServerButton extends JButton {
        private String serverName;
        private boolean selected = false;

        public ServerButton(String serverName) {
            this.serverName = serverName;
            setToolTipText(serverName);
            setPreferredSize(new Dimension(50, 50));
            setMaximumSize(new Dimension(50, 50)); // Fix for BoxLayout
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setAlignmentX(Component.CENTER_ALIGNMENT); // Center in panel
            setBackground(BG_DARKER); // Default background
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (selected) {
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20); // Squircle
            } else {
                g2.setColor(getBackground()); // Use component background
                g2.fillOval(0, 0, getWidth(), getHeight());
            }

            // Initiales du serveur
            String initial = serverName.length() > 0 ? serverName.substring(0, 1).toUpperCase() : "?";
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(initial)) / 2;
            int y = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(initial, x, y);

            if (getModel().isRollover() && !selected) {
                g2.setColor(new Color(255, 255, 255, 50));
                g2.fillOval(0, 0, getWidth(), getHeight());
            }

            g2.dispose();
        }
    }

    /**
     * Scrollbar sombre et fine (Style "Discord").
     */
    public static class SleekScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = new Color(32, 34, 37);
            this.trackColor = new Color(47, 49, 54);
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
            JButton jbutton = new JButton();
            jbutton.setPreferredSize(new Dimension(0, 0));
            jbutton.setMinimumSize(new Dimension(0, 0));
            jbutton.setMaximumSize(new Dimension(0, 0));
            return jbutton;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled())
                return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(32, 34, 37)); // Rail invisible presque

            // La barre elle-même
            g2.setColor(thumbColor);
            g2.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, 8, 8);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(trackColor);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }
    }
}
