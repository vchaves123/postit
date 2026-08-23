package com.recados;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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

        /** Fecha a janela sem apagar a nota. */
        void closeNote(NoteFrame frame);

        void deleteNote(NoteFrame frame);

        void saveNote(Note note);

        void openSettings(NoteFrame origin);

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
    private final GlyphButton pinButton;
    private final Timer saveTimer;

    /** Nota apagada: nada mais deve ser gravado a partir desta janela. */
    private boolean discarded;

    public NoteFrame(Note note, Host host) {
        super("Recados");
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

        this.pinButton = new GlyphButton(Glyph.PIN_ON, "", this::togglePin);
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
        buttons.add(new GlyphButton(Glyph.PLUS, "Nova nota (Ctrl+N)", () -> host.newNote(this)));
        buttons.add(new GlyphButton(Glyph.COLOR, "Trocar a cor (Ctrl+E)", this::cycleColor));
        buttons.add(pinButton);
        // O "x" fecha, nao apaga: numa janela sem decoracao ele e o gesto universal de
        // fechar, e quem clica nele nao esta pedindo para perder o texto.
        buttons.add(new GlyphButton(Glyph.CLOSE, "Fechar esta nota (Ctrl+W) -- nao apaga",
                () -> host.closeNote(this)));
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

    /** Os desenhos dos botoes da barra de titulo. */
    private enum Glyph { PLUS, COLOR, PIN_ON, PIN_OFF, CLOSE }

    /**
     * Botao da barra de titulo. O icone e desenhado, nao escrito: com fonte, glifos como
     * "◑" saem como quadradinho vazio quando a fonte instalada nao tem o caractere.
     *
     * <p>O realce do mouse tambem e pintado aqui dentro, em vez de ligar e desligar
     * {@code setOpaque} -- alternar isso deixa rastro do fundo antigo.
     */
    private final class GlyphButton extends JComponent {

        private static final int SIZE = 22;

        private Glyph glyph;
        private final Runnable action;
        private boolean hovered;

        GlyphButton(Glyph glyph, String tooltip, Runnable action) {
            this.glyph = glyph;
            this.action = action;
            setPreferredSize(new Dimension(SIZE, SIZE));
            setToolTipText(tooltip);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        action.run();
                    }
                }
            });
        }

        void glyph(Glyph glyph) {
            this.glyph = glyph;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            if (hovered) {
                g2.setColor(new Color(0, 0, 0, 28));
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
            }
            g2.setColor(getForeground());

            int box = 10;
            int x = (getWidth() - box) / 2;
            int y = (getHeight() - box) / 2;
            switch (glyph) {
                case PLUS -> {
                    g2.drawLine(x, y + box / 2, x + box, y + box / 2);
                    g2.drawLine(x + box / 2, y, x + box / 2, y + box);
                }
                case COLOR -> {
                    g2.drawOval(x, y, box, box);
                    g2.fillArc(x, y, box, box, 90, 180); // metade cheia: "trocar a cor"
                }
                case PIN_ON -> g2.fillOval(x + 1, y + 1, box - 2, box - 2);
                case PIN_OFF -> g2.drawOval(x + 1, y + 1, box - 2, box - 2);
                case CLOSE -> {
                    g2.drawLine(x, y, x + box, y + box);
                    g2.drawLine(x + box, y, x, y + box);
                }
            }
            g2.dispose();
        }
    }

    // -------------------------------------------------------------- comportamento

    private void installShortcuts() {
        bind("control N", () -> host.newNote(this));
        bind("control W", () -> host.closeNote(this));
        bind("control D", () -> host.deleteNote(this));
        bind("control E", this::cycleColor);
        bind("control T", this::togglePin);
        bind("control COMMA", () -> host.openSettings(this));
        bind("control shift A", host::showAll);
        bind("control Q", host::quit);
    }

    private void bind(String keyStroke, Runnable action) {
        String name = "recados:" + keyStroke;
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
                // Alt+F4 e fechar, igual ao "x": nunca apaga
                host.closeNote(NoteFrame.this);
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
        pinButton.glyph(note.alwaysOnTop() ? Glyph.PIN_ON : Glyph.PIN_OFF);
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
        menu.add(item("Fechar esta nota", () -> host.closeNote(this)));
        menu.add(item("Mostrar todas as notas", host::showAll));
        menu.add(item("Configuracoes...", () -> host.openSettings(this)));
        menu.addSeparator();
        menu.add(item("Apagar esta nota...", () -> host.deleteNote(this)));
        menu.add(item("Sair do Recados", host::quit));
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
        if (discarded) {
            return;
        }
        saveTimer.restart();
    }

    /** Grava agora o que estiver pendente. */
    public void flush() {
        saveTimer.stop();
        if (discarded) {
            return;
        }
        note.text(textArea.getText());
        host.saveNote(note);
    }

    /**
     * Fecha a janela de uma nota que deixou de existir. Tem que passar por aqui em vez de
     * chamar {@code dispose()} direto: dispose nao para o timer de autosave, e um save
     * pendente disparando depois do apagar regrava o arquivo -- a nota ressuscitava na
     * lixeira e em notes ao mesmo tempo, e voltava no proximo inicio.
     */
    public void discard() {
        discarded = true;
        saveTimer.stop();
        dispose();
    }

    /**
     * Pergunta antes de apagar -- sempre, inclusive com a nota em branco. Antes a nota
     * vazia era apagada sem perguntar, e isso transformava um clique errado em perda
     * silenciosa.
     */
    public boolean confirmDelete() {
        int answer = JOptionPane.showConfirmDialog(this,
                "Apagar esta nota? Ela vai para a lixeira do Recados.\n\n"
                        + "Para so tirar da tela, feche com o x ou Ctrl+W.",
                "Recados", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    /** Tira a janela da tela depois de gravar. A nota continua no disco. */
    public void closeWindow() {
        flush();
        dispose();
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
