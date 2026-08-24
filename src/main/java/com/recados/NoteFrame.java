package com.recados;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
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
import javax.swing.TransferHandler;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.List;
import java.util.Optional;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

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
    private static final int MIN_WIDTH = Note.MIN_WIDTH;
    private static final int MIN_HEIGHT = Note.MIN_HEIGHT;

    /** Cabe a alca e os botoes de formatacao, que tem a mesma altura dos da barra de titulo. */
    private static final int FOOTER_HEIGHT = 26;

    /** Azul de link. Mais forte que o texto de qualquer paleta, inclusive a azul. */
    private static final Color LINK_COLOR = new Color(0x0B57D0);

    private final Note note;
    private final Host host;
    private final JTextPane textPane = new JTextPane();
    private final JPanel header = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new BorderLayout());
    private final JPanel formatBar = new JPanel();
    private final GlyphButton pinButton;
    private final Timer saveTimer;

    /**
     * Verdadeiro so enquanto a alca esta sendo arrastada. E a unica forma legitima de mudar
     * o tamanho da nota, entao qualquer outra mudanca e desfeita.
     */
    private boolean userResizing;

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
        textPane.setEditorKit(htmlKit()); // troca o documento: tem de vir antes do resto
        textPane.setOpaque(false);
        textPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        installStyleShortcuts();
        installLinkClicks();
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
        footer.setPreferredSize(new Dimension(0, FOOTER_HEIGHT));
        footer.add(buildFormatBar(), BorderLayout.WEST);
        footer.add(Box.createHorizontalGlue(), BorderLayout.CENTER);
        footer.add(new ResizeGrip(), BorderLayout.EAST);
        attachPopup(footer);
        return footer;
    }

    /**
     * A barra de formatacao, no rodape. Fica embaixo, e nao na barra de titulo, para nao
     * misturar acoes da <i>janela</i> (nova nota, cor, fixar, minimizar) com acoes do
     * <i>texto</i> -- e porque a nota mais estreita nao teria largura para as duas coisas.
     *
     * <p>Aparece so com a nota em foco: nota que voce esta apenas lendo nao precisa dela. A
     * altura do rodape nao muda quando ela aparece, de proposito -- se mudasse, o texto
     * pularia (e a rolagem escorregaria) a cada vez que a nota ganha ou perde o foco.
     */
    private JComponent buildFormatBar() {
        formatBar.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 2));
        formatBar.setOpaque(false);
        formatBar.setVisible(false); // a nota nasce sem foco
        formatBar.add(new GlyphButton(Glyph.BOLD, "Negrito (Ctrl+B)",
                () -> applyStyle(new StyledEditorKit.BoldAction())));
        formatBar.add(new GlyphButton(Glyph.ITALIC, "Italico (Ctrl+I)",
                () -> applyStyle(new StyledEditorKit.ItalicAction())));
        formatBar.add(new GlyphButton(Glyph.UNDERLINE, "Sublinhado (Ctrl+U)",
                () -> applyStyle(new StyledEditorKit.UnderlineAction())));
        formatBar.add(new GlyphButton(Glyph.LIST, "Inserir lista", this::insertList));
        formatBar.add(new GlyphButton(Glyph.LINK, "Inserir link", this::promptLink));
        formatBar.add(new GlyphButton(Glyph.ERASER,
                "Limpar formatacao da selecao (ou da nota toda)", this::clearFormatting));
        attachPopup(formatBar);
        return formatBar;
    }

    /** Se a barra de formatacao esta na tela. Usado tambem pelas checagens. */
    public boolean formatBarVisible() {
        return formatBar.isVisible();
    }

    /**
     * Se algum botao da barra pode receber o foco. Tem de ser {@code false}: botao que rouba
     * o foco do editor apaga a selecao <i>na tela</i> -- voce marca a palavra, clica no B, e
     * nao ve mais o que era a selecao. Usado pelas checagens para nao voltar a acontecer.
     */
    public boolean formatBarStealsFocus() {
        for (Component child : formatBar.getComponents()) {
            if (child.isFocusable()) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------- texto rico

    /**
     * Monta o editor em HTML. E o formato do documento porque e o que o Windows aceita ao
     * colar em e-mail e navegador, e o que da listas e links quase de graca.
     *
     * <p>Publico e estatico para as checagens medirem o recuo da lista com o mesmo CSS que
     * a nota usa -- se fosse copia, a medida deixaria de valer no dia em que o CSS mudasse.
     */
    public static HTMLEditorKit htmlKit() {
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet css = kit.getStyleSheet();
        // a fonte da nota vem daqui: num documento HTML o setFont do JTextPane nao manda
        css.addRule("body { font-family: 'Segoe UI', sans-serif; font-size: 11pt; margin: 0; }");
        css.addRule("p { margin: 0; }");
        // O recuo padrao do Swing para lista e 50px, o que numa nota de 280px de largura come
        // um sexto da linha e joga o marcador para o meio do nada. Com 6px o ponto cai na
        // primeira coluna do texto -- medido: marcador em x=9 contra texto normal em x=10 --
        // e o texto do item fica 7px a direita, o suficiente para se ler como lista.
        css.addRule("ul, ol { margin-top: 0; margin-bottom: 0; margin-left: 6px;"
                + " padding-left: 0; }");
        css.addRule("a { text-decoration: underline; }");
        return kit;
    }

    /**
     * Le o conteudo na melhor forma disponivel: HTML, depois o RTF de notas antigas
     * (convertido na hora), depois o texto puro. Nunca falha para o usuario -- na pior das
     * hipoteses a nota abre sem formatacao, e nao vazia.
     */
    private void loadContent() {
        String html = note.html();
        if (html.isBlank()) {
            html = HtmlText.rtfToHtml(note.rtf());
        }
        if (html.isBlank()) {
            html = HtmlText.plainToHtml(note.text());
        }
        textPane.setText(html);
        textPane.setCaretPosition(0);
        applyTextColor();
    }

    /**
     * A copia em texto puro. Vem aparada porque o HTMLDocument comeca com uma quebra de
     * linha propria, e sem isso o {@code text=} de toda nota comecaria com uma linha vazia --
     * o que aparece na lista da bandeja e em qualquer grep.
     */
    private String plainText() {
        Document doc = textPane.getDocument();
        try {
            return doc.getText(0, doc.getLength()).strip();
        } catch (BadLocationException e) {
            return note.text(); // nao troca por vazio: perder texto e pior que nao salvar
        }
    }

    /** O documento em HTML, ou vazio se falhar -- perder a formatacao e melhor que o texto. */
    private String htmlText() {
        Document doc = textPane.getDocument();
        if (doc.getLength() == 0) {
            return "";
        }
        StringWriter out = new StringWriter();
        try {
            textPane.getEditorKit().write(out, doc, 0, doc.getLength());
            return out.toString();
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

    /**
     * Lista com marcadores. Com texto selecionado, <b>cada linha selecionada vira um item</b>
     * -- que e o que se espera de um botao de lista; inserir um item vazio no meio do texto
     * marcado nao ajudaria ninguem. Sem selecao, insere um item vazio para digitar.
     *
     * <p>Linha aqui e linha de verdade, do jeito que o Swing guarda: as linhas de uma nota
     * sao {@code <br>} dentro de um paragrafo so, nao um paragrafo cada. E a selecao e
     * esticada para as bordas das linhas -- quem marcou meia palavra da primeira linha quis
     * a linha inteira na lista.
     */
    public void insertList() {
        if (!(textPane.getDocument() instanceof HTMLDocument doc)) {
            return;
        }
        int selectionStart = textPane.getSelectionStart();
        int selectionEnd = textPane.getSelectionEnd();
        if (selectionEnd <= selectionStart) {
            insertHtml("<ul><li></li></ul>", HTML.Tag.UL);
            return;
        }
        try {
            int from = lineStart(doc, skipEmptyLead(doc, selectionStart, selectionEnd));
            int to = lineEnd(doc, Math.max(from, selectionEnd - 1));
            List<String> items = HtmlText.lines(HtmlText.body(rangeAsHtml(from, to)));
            if (items.isEmpty()) {
                insertHtml("<ul><li></li></ul>", HTML.Tag.UL);
                return;
            }
            StringBuilder list = new StringBuilder("<ul>");
            for (String item : items) {
                list.append("<li>").append(item).append("</li>");
            }
            list.append("</ul>");

            // A lista e um bloco: ela mesma separa o que vem antes e depois. As quebras que
            // delimitavam as linhas convertidas viram linha vazia se ficarem, entao saem com
            // elas. A da frente so sai se nao for a primeira posicao util do documento.
            int removeFrom = from > 1 && isBreak(doc, from - 1) ? from - 1 : from;
            int removeTo = to < doc.getLength() && isBreak(doc, to) ? to + 1 : to;

            doc.remove(removeFrom, removeTo - removeFrom);

            // Se as linhas convertidas eram um paragrafo inteiro -- e o que acontece com
            // texto colado de fora, que chega como um <p> por linha --, sobra um paragrafo
            // vazio no lugar. Trocar o elemento pela lista nao deixa esse resto; inserir no
            // cursor deixaria uma linha em branco antes dela.
            Element paragraph = doc.getParagraphElement(removeFrom);
            if (paragraph.getEndOffset() - paragraph.getStartOffset() <= 1) {
                doc.setOuterHTML(paragraph, list.toString());
                scheduleSave();
                return;
            }
            textPane.setCaretPosition(removeFrom);
            applyStyle(new HTMLEditorKit.InsertHTMLTextAction("lista", list.toString(),
                    HTML.Tag.BODY, HTML.Tag.UL));
        } catch (BadLocationException | IOException e) {
            System.err.println("Nao foi possivel transformar a selecao em lista: "
                    + e.getMessage());
        }
    }

    /** O HTML de um trecho do documento, com a formatacao de dentro dele. */
    private String rangeAsHtml(int from, int to) {
        StringWriter out = new StringWriter();
        try {
            textPane.getEditorKit().write(out, textPane.getDocument(), from, to - from);
            return out.toString();
        } catch (IOException | BadLocationException e) {
            return ""; // sem HTML nao ha o que dividir; o chamador insere item vazio
        }
    }

    /**
     * Pula a quebra de linha que o HTMLDocument mantem no comeco do documento. Sem isso, uma
     * selecao que comeca em zero (Ctrl+A, por exemplo) pararia nela e a lista sairia vazia.
     */
    private static int skipEmptyLead(HTMLDocument doc, int start, int end) throws BadLocationException {
        int at = start;
        while (at < end && "\n".equals(doc.getText(at, 1))) {
            at++;
        }
        return at;
    }

    /** O comeco da linha que contem esta posicao. */
    private static int lineStart(HTMLDocument doc, int offset) {
        int limit = doc.getParagraphElement(offset).getStartOffset();
        int at = offset;
        while (at > limit && !isBreak(doc, at - 1)) {
            at--;
        }
        return at;
    }

    /** O fim da linha que contem esta posicao, sem incluir a quebra. */
    private static int lineEnd(HTMLDocument doc, int offset) {
        int limit = Math.min(doc.getParagraphElement(offset).getEndOffset(), doc.getLength());
        int at = offset;
        while (at < limit && !isBreak(doc, at)) {
            at++;
        }
        return at;
    }

    private static boolean isBreak(HTMLDocument doc, int offset) {
        return HTML.Tag.BR.toString().equals(doc.getCharacterElement(offset).getName());
    }

    /**
     * Insere HTML no ponto do cursor usando a acao do proprio kit, que cria os elementos
     * pelo parser em vez de na mao.
     *
     * <p>O desvio do cursor em zero nao e capricho: no offset 0 a
     * {@code InsertHTMLTextAction} nao insere nada e nao reclama -- e a nota abre justamente
     * com o cursor em 0, entao lista e link sumiriam em silencio se o usuario nao clicasse
     * antes. Documento vazio tambem nao tem onde ancorar, e ai o jeito e montar o corpo.
     */
    private void insertHtml(String html, HTML.Tag addTag) {
        if (textPane.getDocument().getLength() == 0) {
            textPane.setText("<html><body>" + html + "</body></html>");
            textPane.setCaretPosition(textPane.getDocument().getLength());
            scheduleSave();
            return;
        }
        if (textPane.getCaretPosition() == 0) {
            textPane.setCaretPosition(textPane.getDocument().getLength());
        }
        applyStyle(new HTMLEditorKit.InsertHTMLTextAction("insere", html,
                HTML.Tag.BODY, addTag));
    }

    /** Pergunta o endereco e transforma a selecao em link. */
    private void promptLink() {
        String selected = textPane.getSelectedText();
        String url = JOptionPane.showInputDialog(this,
                selected == null || selected.isBlank()
                        ? "Endereco do link:"
                        : "Endereco para \"" + selected.strip() + "\":",
                "https://");
        if (url != null && !url.isBlank()) {
            insertLink(url, selected);
        }
    }

    /**
     * Transforma a selecao em link, ou insere o endereco como texto quando {@code label} vem
     * vazio. O href fica no atributo {@link HTML.Tag#A}, que e o que o escritor de HTML
     * transforma de volta em {@code <a href>}.
     */
    public void insertLink(String url, String label) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (label == null || label.isBlank()) {
            label = url;
        }

        // A ancora vai como HTML, e nao como atributo de caractere: marcar o texto com
        // HTML.Tag.A faz o escritor emitir <a href><u><p-implied></u></a> sem o rotulo.
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        if (end > start) {
            try {
                textPane.getDocument().remove(start, end - start);
            } catch (BadLocationException e) {
                System.err.println("Nao foi possivel substituir a selecao: " + e.getMessage());
                return;
            }
        }
        insertHtml("<a href=\"" + HtmlText.escapeHtml(url.strip()) + "\">"
                + HtmlText.escapeHtml(label) + "</a>", HTML.Tag.A);
    }

    /**
     * Seleciona um trecho do texto, como o usuario faria com o mouse. Existe para as
     * checagens poderem exercitar as acoes que dependem de selecao.
     */
    public void select(int start, int end) {
        textPane.select(start, end);
    }

    /** Copia a nota inteira, com formatacao, para colar em e-mail ou navegador. */
    public void copyAll() {
        copyAllTo(Toolkit.getDefaultToolkit().getSystemClipboard());
    }

    /**
     * Recebe o destino de proposito: assim as checagens conferem o que sai daqui sem mexer na
     * area de transferencia do usuario.
     */
    public void copyAllTo(Clipboard clipboard) {
        textPane.selectAll();
        textPane.getTransferHandler().exportToClipboard(textPane, clipboard, TransferHandler.COPY);
    }

    /** O href sob uma posicao do texto, quando ha um. */
    private Optional<String> linkAt(int offset) {
        if (!(textPane.getDocument() instanceof HTMLDocument doc)) {
            return Optional.empty();
        }
        AttributeSet attrs = doc.getCharacterElement(offset).getAttributes();
        Object anchor = attrs.getAttribute(HTML.Tag.A);
        if (anchor instanceof AttributeSet set) {
            Object href = set.getAttribute(HTML.Attribute.HREF);
            return href == null ? Optional.empty() : Optional.of(href.toString());
        }
        return Optional.empty();
    }

    /**
     * Ctrl+clique abre o link. Clique simples nao serve: o painel e editavel, e ali o clique
     * e do cursor de texto -- por isso tambem nao da para usar HyperlinkListener, que so
     * dispara em painel somente-leitura.
     */
    private void installLinkClicks() {
        textPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || !e.isControlDown()) {
                    return;
                }
                linkAt(textPane.viewToModel2D(e.getPoint())).ifPresent(NoteFrame::browse);
            }
        });
    }

    private static void browse(String href) {
        if (!Desktop.isDesktopSupported()
                || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(href));
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            System.err.println("Nao foi possivel abrir " + href + ": " + e.getMessage());
        }
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
     * A cor do texto vem da paleta da nota, nao do documento. Sem reaplicar aqui, o HTML traz
     * de volta a cor de quando foi gravado e trocar a cor da nota deixava o texto na cor velha.
     * Os links ficam de fora: azul de link nao e decoracao, e sinal de que da para clicar.
     */
    private void applyTextColor() {
        Color color = note.palette().text();
        textPane.setForeground(color);
        textPane.setCaretColor(color);
        StyledDocument doc = textPane.getStyledDocument();
        if (doc.getLength() == 0) {
            return;
        }
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        doc.setCharacterAttributes(0, doc.getLength(), attrs, false);

        if (doc instanceof HTMLDocument html) {
            SimpleAttributeSet linkAttrs = new SimpleAttributeSet();
            StyleConstants.setForeground(linkAttrs, LINK_COLOR);
            int position = 0;
            while (position < doc.getLength()) {
                Element run = html.getCharacterElement(position);
                int start = run.getStartOffset();
                int end = run.getEndOffset();
                if (run.getAttributes().getAttribute(HTML.Tag.A) != null) {
                    doc.setCharacterAttributes(start, end - start, linkAttrs, false);
                }
                position = Math.max(end, position + 1);
            }
        }
    }

    /** Os desenhos dos botoes: os quatro primeiros da barra de titulo, o resto do rodape. */
    private enum Glyph {
        PLUS, COLOR, PIN_ON, PIN_OFF, MINIMIZE,
        BOLD, ITALIC, UNDERLINE, LIST, LINK, ERASER
    }

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
            // Nao pode receber o foco: botao que rouba o foco do editor apaga a selecao na
            // tela, e quem clica no B depois de marcar uma palavra perde de vista o que
            // marcou. Assim o clique executa a acao e o cursor de texto fica onde estava.
            setFocusable(false);
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

                // Estes tres sao letras, e nao desenho: B, I e U existem em qualquer fonte
                // instalada. O quadradinho vazio que apareceu antes era com simbolo ("◑"),
                // que a fonte pode nao ter -- ASCII nao corre esse risco.
                case BOLD -> drawLetter(g2, "B", Font.BOLD, false);
                case ITALIC -> drawLetter(g2, "I", Font.ITALIC, false);
                case UNDERLINE -> drawLetter(g2, "U", Font.PLAIN, true);

                case LIST -> {
                    for (int row = 0; row < 3; row++) {
                        int ly = y + row * 4 + 1;
                        g2.fillRect(x, ly, 2, 2);
                        g2.drawLine(x + 4, ly + 1, x + box, ly + 1);
                    }
                }
                // dois elos de corrente, um enganchado no outro
                case LINK -> {
                    g2.drawRoundRect(x, y + 3, 6, 5, 4, 4);
                    g2.drawRoundRect(x + 4, y + 3, 6, 5, 4, 4);
                }
                // borracha inclinada sobre a linha do papel
                case ERASER -> {
                    g2.drawPolygon(new int[] {x + 2, x + 7, x + box, x + 5},
                            new int[] {y + 5, y, y + 3, y + 8}, 4);
                    g2.drawLine(x, y + box, x + box, y + box);
                }
            }
            g2.dispose();
        }

        /** Letra centralizada no botao, com sublinhado opcional. */
        private void drawLetter(Graphics2D g2, String letter, int style, boolean underline) {
            g2.setFont(new Font(Font.SANS_SERIF, style, 12));
            FontMetrics metrics = g2.getFontMetrics();
            int width = metrics.stringWidth(letter);
            int tx = (getWidth() - width) / 2;
            int ty = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            g2.drawString(letter, tx, ty);
            if (underline) {
                g2.drawLine(tx, ty + 2, tx + width, ty + 2);
            }
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
                if (userResizing) {
                    note.size(getWidth(), getHeight());
                    scheduleSave();
                    return;
                }
                // Ninguem puxou a alca, entao esta mudanca nao foi pedida. Acontece ao passar
                // para um monitor de escala diferente: o Java reinterpreta 280x260 como
                // 350x325 num monitor a 125%, e gravar isso inflava a nota 25% por travessia.
                if (getWidth() != note.width() || getHeight() != note.height()) {
                    setSize(note.width(), note.height());
                }
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                showFormatBar(true);
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                showFormatBar(false);
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

    /** Mostra ou esconde a barra de formatacao sem mexer na altura do rodape. */
    private void showFormatBar(boolean show) {
        if (formatBar.isVisible() != show) {
            formatBar.setVisible(show);
            footer.revalidate();
            footer.repaint();
        }
    }

    private void applyPalette() {
        Palette palette = note.palette();
        getContentPane().setBackground(palette.body());
        header.setBackground(palette.header());
        footer.setBackground(palette.body());
        textPane.setSelectionColor(palette.header().darker());
        applyTextColor();
        paintForeground(header, palette.text());
        paintForeground(footer, palette.text()); // os botoes de formatacao tambem
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
        menu.add(item("Inserir lista", this::insertList));
        menu.add(item("Inserir link...", this::promptLink));
        menu.addSeparator();
        menu.add(item("Copiar tudo (com formatacao)", this::copyAll));
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
        note.html(htmlText());
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
     * Nota sem conteudo nenhum. Vale o texto puro do documento: uma lista vazia ou um
     * paragrafo solto que o editor deixou para tras nao fazem a nota ter conteudo.
     */
    public boolean isBlank() {
        return plainText().isEmpty();
    }

    /**
     * Pergunta antes de apagar. Nota em branco nao passa por aqui -- {@link #isBlank()} --
     * porque nao ha o que perder. Nota com conteudo pergunta sempre: antes a vazia era
     * apagada sem perguntar e a cheia tambem, e isso transformava um clique errado em perda
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

    /**
     * Arrasta a janela pela barra de titulo.
     *
     * <p>A posicao e recalculada do ponteiro a cada evento, e nao somando deslocamentos sobre
     * a posicao inicial. Com dois monitores em escalas diferentes, as coordenadas mudam de
     * significado ao atravessar a borda, e o acumulo saia do controle -- a nota fugia do
     * cursor e voltava para o monitor de origem. Assim, no pior caso ha um tranco de um
     * quadro, e a janela continua colada no ponteiro.
     */
    private final class DragSupport extends MouseAdapter {
        private Point grabOffset;

        @Override
        public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                Point pointer = e.getLocationOnScreen();
                Point origin = getLocation();
                grabOffset = new Point(pointer.x - origin.x, pointer.y - origin.y);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            grabOffset = null;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (grabOffset == null) {
                return;
            }
            Point pointer = e.getLocationOnScreen();
            setLocation(pointer.x - grabOffset.x, pointer.y - grabOffset.y);
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
                    userResizing = true;
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    grabScreenPoint = null;
                    userResizing = false;
                    flush(); // grava o tamanho final da alca
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
