package com.postit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A janela de uma nota: sem decoracao do sistema, arrastavel pela barra de titulo propria
 * e redimensionavel pela alca no canto inferior direito.
 */
public final class NoteFrame extends JFrame {

    /** O que a janela precisa pedir ao aplicativo. */
    public interface Host {
        void newNote(NoteFrame origin);

        void deleteNote(NoteFrame frame);

        void saveNote(Note note);

        void showAll();

        void quit();
    }

    private static final int HEADER_HEIGHT = 28;
    private static final int GRIP_SIZE = 14;
    private static final int SAVE_DELAY_MS = 500;
    private static final int MIN_WIDTH = 160;
    private static final int MIN_HEIGHT = 120;

    private final Note note;
    private final Host host;
    private final JTextArea textArea = new JTextArea();
    private final JPanel header = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new BorderLayout());
    private final JLabel pinButton;
    private final Timer saveTimer;

    public NoteFrame(Note note, Host host) {
        super("postit");
        this.note = note;
        this.host = host;
        this.saveTimer = new Timer(SAVE_DELAY_MS, e -> flush());
        this.saveTimer.setRepeats(false);

        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImage(Icons.trayIcon(32));
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setAlwaysOnTop(note.alwaysOnTop());

        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0, 70)));
        setContentPane(root);

        this.pinButton = glyphLabel("", "", this::togglePin);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildBody(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        installShortcuts();
        installGeometryTracking();
        applyPalette();
        refreshPinButton();

        setSize(note.width(), note.height());
        setLocation(note.x(), note.y());
        textArea.setText(note.text());
        textArea.setCaretPosition(0);
    }

    public Note note() {
        return note;
    }

    // ------------------------------------------------------------------ montagem

    private JComponent buildHeader() {
        header.setPreferredSize(new Dimension(0, HEADER_HEIGHT));
        header.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 4));

        JLabel grabArea = new JLabel();
        grabArea.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        header.add(grabArea, BorderLayout.CENTER);

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(glyphLabel("+", "Nova nota (Ctrl+N)", () -> host.newNote(this)));
        buttons.add(glyphLabel("◑", "Trocar a cor (Ctrl+E)", this::cycleColor));
        buttons.add(pinButton);
        buttons.add(glyphLabel("×", "Apagar esta nota (Ctrl+D)", () -> host.deleteNote(this)));
        header.add(buttons, BorderLayout.EAST);

        DragSupport drag = new DragSupport();
        header.addMouseListener(drag);
        header.addMouseMotionListener(drag);
        grabArea.addMouseListener(drag);
        grabArea.addMouseMotionListener(drag);

        attachPopup(header);
        attachPopup(grabArea);
        return header;
    }

    private JComponent buildBody() {
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleSave();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleSave();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                scheduleSave();
            }
        });
        attachPopup(textArea);

        JScrollPane scroll = new JScrollPane(textArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        return scroll;
    }

    private JComponent buildFooter() {
        footer.setPreferredSize(new Dimension(0, GRIP_SIZE));
        footer.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        footer.add(new ResizeGrip(), BorderLayout.EAST);
        attachPopup(footer);
        return footer;
    }

    private JLabel glyphLabel(String glyph, String tooltip, Runnable action) {
        JLabel label = new JLabel(glyph, JLabel.CENTER);
        label.setPreferredSize(new Dimension(22, 20));
        label.setToolTipText(tooltip);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        label.setOpaque(false);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                label.setOpaque(true);
                label.setBackground(new Color(0, 0, 0, 30));
                label.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                label.setOpaque(false);
                label.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    action.run();
                }
            }
        });
        return label;
    }

    // -------------------------------------------------------------- comportamento

    private void installShortcuts() {
        bind("control N", () -> host.newNote(this));
        bind("control D", () -> host.deleteNote(this));
        bind("control E", this::cycleColor);
        bind("control T", this::togglePin);
        bind("control shift A", host::showAll);
        bind("control Q", host::quit);
    }

    private void bind(String keyStroke, Runnable action) {
        String name = "postit:" + keyStroke;
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(keyStroke), name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void installGeometryTracking() {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                note.location(getX(), getY());
                scheduleSave();
            }

            @Override
            public void componentResized(ComponentEvent e) {
                note.size(getWidth(), getHeight());
                applyRoundedShape();
                scheduleSave();
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent e) {
                flush();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                flush();
            }
        });
    }

    private void applyPalette() {
        Palette palette = note.palette();
        getContentPane().setBackground(palette.body());
        header.setBackground(palette.header());
        footer.setBackground(palette.body());
        textArea.setForeground(palette.text());
        textArea.setCaretColor(palette.text());
        textArea.setSelectionColor(palette.header().darker());
        paintForeground(header, palette.text());
        repaint();
    }

    private static void paintForeground(Component component, Color color) {
        component.setForeground(color);
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) {
                paintForeground(child, color);
            }
        }
    }

    private void applyRoundedShape() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (!device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
            return;
        }
        try {
            setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 12, 12));
        } catch (UnsupportedOperationException ignored) {
            // sem cantos arredondados nesta plataforma; a janela continua funcionando
        }
    }

    private void cycleColor() {
        note.colorIndex(Palette.next(note.colorIndex()));
        applyPalette();
        flush(); // escolha explicita do usuario: grava na hora
    }

    private void togglePin() {
        note.alwaysOnTop(!note.alwaysOnTop());
        setAlwaysOnTop(note.alwaysOnTop());
        refreshPinButton();
        flush();
    }

    private void refreshPinButton() {
        pinButton.setText(note.alwaysOnTop() ? "●" : "○");
        pinButton.setToolTipText(note.alwaysOnTop()
                ? "Fixada no topo; clique para soltar (Ctrl+T)"
                : "Solta; clique para fixar no topo (Ctrl+T)");
    }

    private void attachPopup(JComponent component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShow(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShow(e);
            }

            private void maybeShow(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    buildPopup().show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    private JPopupMenu buildPopup() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Nova nota", () -> host.newNote(this)));
        menu.add(item("Trocar a cor", this::cycleColor));
        menu.add(item(note.alwaysOnTop() ? "Soltar do topo" : "Fixar no topo", this::togglePin));
        menu.addSeparator();
        menu.add(item("Mostrar todas as notas", host::showAll));
        menu.addSeparator();
        menu.add(item("Apagar esta nota", () -> host.deleteNote(this)));
        menu.add(item("Sair do postit", host::quit));
        return menu;
    }

    private static JMenuItem item(String text, Runnable action) {
        JMenuItem menuItem = new JMenuItem(text);
        menuItem.addActionListener(e -> action.run());
        return menuItem;
    }

    // -------------------------------------------------------------- persistencia

    /** Marca a nota como suja; o disco so e tocado depois de meio segundo sem digitacao. */
    public void scheduleSave() {
        saveTimer.restart();
    }

    /** Grava agora o que estiver pendente. */
    public void flush() {
        saveTimer.stop();
        note.text(textArea.getText());
        host.saveNote(note);
    }

    /** Pergunta antes de apagar, exceto quando a nota esta vazia. */
    public boolean confirmDelete() {
        if (textArea.getText().isBlank()) {
            return true;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Apagar esta nota? Nao da para desfazer.",
                "postit", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    public void focusText() {
        toFront();
        requestFocus();
        textArea.requestFocusInWindow();
    }

    // ------------------------------------------------- arrastar e redimensionar

    /** Arrasta a janela pela barra de titulo. */
    private final class DragSupport extends MouseAdapter {
        private Point grabScreenPoint;
        private Point windowOrigin;

        @Override
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                grabScreenPoint = e.getLocationOnScreen();
                windowOrigin = getLocation();
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            grabScreenPoint = null;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (grabScreenPoint == null) {
                return;
            }
            Point now = e.getLocationOnScreen();
            setLocation(windowOrigin.x + (now.x - grabScreenPoint.x),
                    windowOrigin.y + (now.y - grabScreenPoint.y));
        }
    }

    /** Alca de redimensionamento no canto inferior direito. */
    private final class ResizeGrip extends JComponent {
        private Point grabScreenPoint;
        private Dimension originalSize;

        ResizeGrip() {
            setPreferredSize(new Dimension(GRIP_SIZE, GRIP_SIZE));
            setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
            setToolTipText("Arraste para redimensionar");
            MouseAdapter handler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    grabScreenPoint = e.getLocationOnScreen();
                    originalSize = NoteFrame.this.getSize();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    grabScreenPoint = null;
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (grabScreenPoint == null) {
                        return;
                    }
                    Point now = e.getLocationOnScreen();
                    int width = Math.max(MIN_WIDTH, originalSize.width + (now.x - grabScreenPoint.x));
                    int height = Math.max(MIN_HEIGHT, originalSize.height + (now.y - grabScreenPoint.y));
                    NoteFrame.this.setSize(width, height);
                }
            };
            addMouseListener(handler);
            addMouseMotionListener(handler);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 70));
            int right = getWidth() - 3;
            int bottom = getHeight() - 3;
            for (int offset = 0; offset < 3; offset++) {
                int shift = offset * 4;
                g2.drawLine(right - shift, bottom, right, bottom - shift);
            }
            g2.dispose();
        }
    }
}
