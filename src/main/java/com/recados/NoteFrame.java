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
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.rtf.RTFEditorKit;

/**
 * A janela de uma nota: sem decoracao do sistema, arrastavel pela barra de titulo propria
 * e redimensionavel pela alca no canto inferior direito.
 */
public final class NoteFrame extends JFrame {

    /** O que a janela precisa pedir ao aplicativo. */
    public interface Host {
        void newNote(NoteFrame origin);

        /** Minimiza: tira a janela da tela sem apagar a nota. */
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

    /** Um por classe: o kit nao guarda estado do documento. */
    private static final RTFEditorKit RTF = new RTFEditorKit();

    /**
     * O RTFEditorKit trabalha em bytes -- passar Reader ou Writer para ele levanta
     * "RTF is an 8-bit format". Latin-1 mapeia byte a caractere sem perder nada, entao o
     * RTF cabe numa String e volta identico.
     */
    private static final Charset RTF_CHARSET = StandardCharsets.ISO_8859_1;

    private final Note note;
    private final Host host;
    private final JTextPane textPane = new JTextPane();
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
        setIconImages(Icons.appIcons());
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
        loadContent();
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
        buttons.add(new GlyphButton(Glyph.MINIMIZE,
                "Minimizar esta nota (Ctrl+W) -- nao apaga, volta pela bandeja",
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
        textPane.setOpaque(false);
        textPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        textPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        installStyleShortcuts();
        textPane.getDocument().addDocumentListener(new DocumentListener() {
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
        attachPopup(textPane);

        JScrollPane scroll = new JScrollPane(textPane);
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

    // -------------------------------------------------------------- texto rico

    /**
     * Le o conteudo da nota: o RTF quando existir, o texto puro quando nao. Nota gravada
     * por uma versao anterior so tem texto puro, e abre normalmente.
     */
    private void loadContent() {
        String rich = note.rtf();
        if (!rich.isBlank()) {
            try {
                Document doc = textPane.getDocument();
                doc.remove(0, doc.getLength());
                RTF.read(new ByteArrayInputStream(rich.getBytes(RTF_CHARSET)), doc, 0);
                textPane.setCaretPosition(0);
                applyTextColor();
                return;
            } catch (IOException | BadLocationException e) {
                System.err.println("Formatacao da nota " + note.id() + " ilegivel ("
                        + e.getMessage() + "); abrindo o texto puro.");
            }
        }
        textPane.setText(note.text());
        textPane.setCaretPosition(0);
        applyTextColor();
    }

    private String plainText() {
        Document doc = textPane.getDocument();
        try {
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            return note.text(); // nao troca por vazio: perder texto e pior que nao salvar
        }
    }

    /** O documento em RTF, ou vazio se falhar -- perder a formatacao e melhor que o texto. */
    private String richText() {
        Document doc = textPane.getDocument();
        if (doc.getLength() == 0) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            RTF.write(out, doc, 0, doc.getLength());
            return out.toString(RTF_CHARSET);
        } catch (IOException | BadLocationException e) {
            System.err.println("Nao foi possivel gravar a formatacao da nota " + note.id()
                    + ": " + e.getMessage());
            return "";
        }
    }

    private void installStyleShortcuts() {
        bindStyle("control B", new StyledEditorKit.BoldAction());
        bindStyle("control I", new StyledEditorKit.ItalicAction());
        bindStyle("control U", new StyledEditorKit.UnderlineAction());
    }

    private void bindStyle(String keyStroke, Action action) {
        String name = "recados-estilo:" + keyStroke;
        textPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(keyStroke), name);
        textPane.getActionMap().put(name, action);
    }

    /** Dispara uma acao de estilo como se tivesse vindo do teclado. */
    private void applyStyle(Action action) {
        action.actionPerformed(new ActionEvent(textPane, ActionEvent.ACTION_PERFORMED, ""));
        scheduleSave();
    }

    /** Tira negrito, italico e sublinhado da selecao -- ou da nota toda, se nao houver. */
    private void clearFormatting() {
        StyledDocument doc = textPane.getStyledDocument();
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        if (start == end) {
            start = 0;
            end = doc.getLength();
        }
        SimpleAttributeSet plain = new SimpleAttributeSet();
        StyleConstants.setBold(plain, false);
        StyleConstants.setItalic(plain, false);
        StyleConstants.setUnderline(plain, false);
        doc.setCharacterAttributes(start, end - start, plain, false);
        scheduleSave();
    }

    /**
     * A cor do texto vem da paleta da nota, nao do RTF. Sem reaplicar aqui, o RTF traz de
     * volta a cor de quando foi gravado e trocar a cor da nota deixava o texto na cor velha.
     */
    private void applyTextColor() {
        Color color = note.palette().text();
        textPane.setForeground(color);
        textPane.setCaretColor(color);
        StyledDocument doc = textPane.getStyledDocument();
        if (doc.getLength() > 0) {
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, color);
            doc.setCharacterAttributes(0, doc.getLength(), attrs, false);
        }
    }

    /** Os desenhos dos botoes da barra de titulo. */
    private enum Glyph { PLUS, COLOR, PIN_ON, PIN_OFF, MINIMIZE }

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
                // barra embaixo, como o minimizar do Windows: a nota sai da tela e
                // continua existindo. Um "x" prometia fechar e cumpria apagar.
                case MINIMIZE -> g2.drawLine(x, y + box, x + box, y + box);
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
                // Só grava. Nao minimiza: WM_CLOSE tambem chega quando alguem encerra o
                // processo ou o Windows desliga, e minimizar aqui gravava visible=false em
                // todas as notas -- o proximo inicio abria sem janela nenhuma.
                flush();
            }
        });
    }

    private void applyPalette() {
        Palette palette = note.palette();
        getContentPane().setBackground(palette.body());
        header.setBackground(palette.header());
        footer.setBackground(palette.body());
        textPane.setSelectionColor(palette.header().darker());
        applyTextColor();
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
        menu.add(buildFormatMenu());
        menu.addSeparator();
        menu.add(item("Nova nota", () -> host.newNote(this)));
        menu.add(item("Trocar a cor", this::cycleColor));
        menu.add(item(note.alwaysOnTop() ? "Soltar do topo" : "Fixar no topo", this::togglePin));
        menu.addSeparator();
        menu.add(item("Minimizar esta nota", () -> host.closeNote(this)));
        menu.add(item("Mostrar todas as notas", host::showAll));
        menu.add(item("Configuracoes...", () -> host.openSettings(this)));
        menu.addSeparator();
        menu.add(item("Apagar esta nota...", () -> host.deleteNote(this)));
        menu.add(item("Sair do Recados", host::quit));
        return menu;
    }

    /** Formatacao da selecao; sem selecao, o estilo vale para o que for digitado em seguida. */
    private JMenu buildFormatMenu() {
        JMenu menu = new JMenu("Formatar");
        menu.add(item("Negrito (Ctrl+B)", () -> applyStyle(new StyledEditorKit.BoldAction())));
        menu.add(item("Italico (Ctrl+I)", () -> applyStyle(new StyledEditorKit.ItalicAction())));
        menu.add(item("Sublinhado (Ctrl+U)",
                () -> applyStyle(new StyledEditorKit.UnderlineAction())));
        menu.addSeparator();
        menu.add(item("Limpar formatacao", this::clearFormatting));
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
        note.text(plainText());
        note.rtf(richText());
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
                        + "Para so tirar da tela, minimize com o botao da barra ou Ctrl+W.",
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
        textPane.requestFocusInWindow();
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
