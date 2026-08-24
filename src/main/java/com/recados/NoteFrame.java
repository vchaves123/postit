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
import java.awt.Rectangle;
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
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
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

        /** {@code false} se a nota nao chegou ao disco: a janela vai tentar de novo. */
        boolean saveNote(Note note);

        void openSettings(NoteFrame origin);

        void showAll();

        void quit();
    }

    private static final int HEADER_HEIGHT = 28;
    private static final int GRIP_SIZE = 14;
    private static final int SAVE_DELAY_MS = 500;

    /** Quantas vezes o autosave insiste depois de uma falha de gravacao. */
    private static final int SAVE_RETRIES = 5;

    private static final int MIN_WIDTH = Note.MIN_WIDTH;
    private static final int MIN_HEIGHT = Note.MIN_HEIGHT;

    /** Cabe a alca e os botoes de formatacao, que tem a mesma altura dos da barra de titulo. */
    private static final int FOOTER_HEIGHT = 26;

    /** Estreita: a barra e para saber onde voce esta, nao um controle para se mirar. */
    private static final int SCROLLBAR_WIDTH = 9;

    /** Azul de link. Mais forte que o texto de qualquer paleta, inclusive a azul. */
    private static final Color LINK_COLOR = new Color(0x0B57D0);

    /**
     * A familia do trecho monoespacado, escrita como o CSS espera: o Swing entende a lista e
     * fica na primeira que existir, e o navegador que abrir o arquivo faz o mesmo. Uma
     * generica no fim ({@code monospace}) garante largura fixa em qualquer sistema.
     */
    static final String MONO_FAMILY_CSS = "Consolas, 'Courier New', monospace";

    /** Os tamanhos que o Ctrl+ e o Ctrl- percorrem, em pontos. */
    private static final int[] FONT_SIZES = {8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28};

    /**
     * Quanto tempo sem editar fecha um passo de desfazer. Sem isto, cada tecla seria um
     * passo e voltar uma frase custaria trinta Ctrl+Z; com isto, uma digitacao corrida volta
     * de uma vez, e uma pausa marca onde ela termina.
     */
    private static final int UNDO_GROUP_MS = 700;

    private final Note note;
    private final Host host;
    private final JTextPane textPane = new JTextPane();
    private final JPanel header = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new BorderLayout());
    private final JPanel formatBar = new JPanel();
    private JScrollBar scrollBar;
    private final GlyphButton pinButton;
    private final Timer saveTimer;

    /**
     * Verdadeiro so enquanto a alca esta sendo arrastada. E a unica forma legitima de mudar
     * o tamanho da nota, entao qualquer outra mudanca e desfeita.
     */
    private boolean userResizing;

    /** Nota apagada: nada mais deve ser gravado a partir desta janela. */
    private boolean discarded;

    private final UndoManager undoManager = new UndoManager();

    /**
     * O passo de desfazer que esta aberto. Enquanto ele existe, o {@link UndoManager}
     * absorve as edicoes seguintes dentro dele -- e assim que uma acao que mexe no documento
     * varias vezes (virar lista, inserir link) volta com um Ctrl+Z so.
     */
    private CompoundEdit undoGroup;

    private long lastEditAt;

    /**
     * Falso enquanto o programa, e nao o usuario, mexe no documento: a carga da nota e a
     * recoloracao pela paleta. Sem isto o primeiro Ctrl+Z de uma nota recem-aberta apagaria
     * o texto inteiro -- a carga e uma insercao como qualquer outra para o documento.
     */
    private boolean recordingUndo = true;

    /** Falhas de gravacao seguidas; zera assim que uma gravacao passa. */
    private int failedSaves;

    public NoteFrame(Note note, Host host) {
        super("Recados");
        // idempotente: a nota pode nascer pelo aplicativo ou por uma checagem, e as duas
        // portas de entrada precisam do diario ligado quando -Drecados.trace estiver posto
        Trace.instalar();
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
        buttons.add(new GlyphButton(Glyph.PLUS, "Nova nota (Ctrl+N)",
                traced("botao PLUS", () -> host.newNote(this))));
        buttons.add(new GlyphButton(Glyph.COLOR, "Trocar a cor (Ctrl+E)",
                traced("botao COLOR", this::cycleColor)));
        buttons.add(pinButton);
        buttons.add(new GlyphButton(Glyph.MINIMIZE,
                "Minimizar esta nota (Ctrl+W) -- nao apaga, volta pela bandeja",
                traced("botao MINIMIZE", () -> host.closeNote(this))));
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
        installUndo();

        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        // A barra de rolagem do sistema chega cinza, e uma faixa cinza no meio de uma nota
        // colorida se anuncia como "componente", nao como parte do papel.
        scrollBar = scroll.getVerticalScrollBar();
        scrollBar.setUI(new NoteScrollBarUI());
        scrollBar.setPreferredSize(new Dimension(SCROLLBAR_WIDTH, 0));
        scrollBar.setOpaque(false);
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
        formatButton(Glyph.BOLD, "Negrito (Ctrl+B)",
                () -> applyStyle(new StyledEditorKit.BoldAction()));
        formatButton(Glyph.ITALIC, "Italico (Ctrl+I)",
                () -> applyStyle(new StyledEditorKit.ItalicAction()));
        formatButton(Glyph.UNDERLINE, "Sublinhado (Ctrl+U)",
                () -> applyStyle(new StyledEditorKit.UnderlineAction()));
        // 4px mais largo que os vizinhos: o "</>" sao tres elementos, e em 14px eles se
        // encostam. A diferenca de largura nao se nota; o borrao se notava. Estes 4px sao a
        // razao de a largura minima da nota ser 180, e nao 160.
        formatBar.add(new GlyphButton(Glyph.MONOSPACED,
                "Fonte monoespacada na selecao -- sem selecao, na nota toda",
                traced("botao MONOSPACED", this::toggleMonospaced), GlyphButton.SMALL_SIZE + 4));
        formatButton(Glyph.LIST, "Lista com marcadores -- cada linha selecionada vira um item",
                this::insertList);
        formatButton(Glyph.NUMBERED_LIST,
                "Lista numerada -- cada linha selecionada vira um item", this::insertNumberedList);
        formatButton(Glyph.LINK, "Inserir link", this::promptLink);
        formatButton(Glyph.ERASER,
                "Limpar formatacao da selecao (ou da nota toda)", this::clearFormatting);
        attachPopup(formatBar);
        return formatBar;
    }

    private void formatButton(Glyph glyph, String tooltip, Runnable action) {
        formatBar.add(new GlyphButton(glyph, tooltip,
                traced("botao " + glyph, action), GlyphButton.SMALL_SIZE));
    }

    /** Se a barra de formatacao esta na tela. Usado tambem pelas checagens. */
    public boolean formatBarVisible() {
        return formatBar.isVisible();
    }

    /**
     * Se a barra inteira, mais a alca de redimensionar, cabe numa nota desta largura. Cada
     * botao novo aperta esse limite -- foi o setimo (a lista numerada) que obrigou os botoes
     * do rodape a serem menores que os da barra de titulo. A checagem confere no menor
     * tamanho de nota, que e onde a conta estoura primeiro.
     */
    public boolean formatBarFitsIn(int noteWidth) {
        int borda = 2; // a linha de contorno da nota, um pixel de cada lado
        return formatBar.getPreferredSize().width + GRIP_SIZE + borda <= noteWidth;
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

    // -------------------------------------------------------------- desfazer

    /**
     * Desfazer e refazer. O Swing nao traz isto de graca: o documento avisa cada edicao, e
     * quem guarda a pilha e o {@link UndoManager}.
     */
    private void installUndo() {
        undoManager.setLimit(200);
        textPane.getDocument().addUndoableEditListener(event -> {
            if (!recordingUndo) {
                return;
            }
            long now = System.currentTimeMillis();
            if (undoGroup == null || now - lastEditAt > UNDO_GROUP_MS) {
                openUndoGroup();
            }
            // vai para dentro do grupo aberto: o UndoManager oferece a edicao nova ao ultimo
            // edit da pilha antes de empilhar, e um CompoundEdit sem end() aceita
            undoManager.addEdit(event.getEdit());
            lastEditAt = now;
        });
    }

    private void openUndoGroup() {
        closeUndoGroup();
        undoGroup = new CompoundEdit();
        undoManager.addEdit(undoGroup);
    }

    private void closeUndoGroup() {
        if (undoGroup != null) {
            undoGroup.end();
            undoGroup = null;
        }
    }

    /**
     * Roda uma acao que reorganiza a estrutura do documento -- virar lista, quebrar item,
     * limpar formatacao, inserir link -- como <b>um</b> passo de desfazer, guardando uma
     * <b>foto</b> do HTML antes e depois em vez das edicoes que o Swing gerou pelo caminho.
     *
     * <p>A foto nao e capricho. As operacoes estruturais do {@code HTMLDocument}
     * ({@code insertAfterEnd}, {@code removeElement}, {@code setOuterHTML}) empilham edicoes
     * que nao voltam na ordem inversa: o Ctrl+Z estourava com {@code CannotUndoException} no
     * meio do caminho e deixava o documento pela metade -- uma lista com um item que ja nao
     * pertencia a ela. Com a arvore nesse estado, o calculo de linhas do Swing
     * ({@code FlowView.layoutRow}) entrava em circulo, criava centenas de milhares de
     * pedacos de linha e comia a interface junto com alguns GB de memoria. Foi assim que a
     * janela "travava" depois de um ENTER dentro da lista seguido de Ctrl+Z.
     *
     * <p>Uma foto sempre volta, porque nao depende de a edicao ser reversivel. O preco e
     * guardar o HTML da nota duas vezes por comando -- alguns KB, para um texto que cabe
     * numa janelinha de recado.
     */
    private void asOneUndoStep(Runnable action) {
        String antes = snapshot();
        int caretAntes = textPane.getCaretPosition();
        withoutUndo(action);
        String depois = snapshot();
        int caretDepois = textPane.getCaretPosition();
        if (antes.equals(depois)) {
            return; // comando que nao mudou nada nao merece um Ctrl+Z
        }
        closeUndoGroup();
        undoManager.addEdit(new SnapshotEdit(antes, caretAntes, depois, caretDepois));
    }

    /** O documento inteiro em HTML, nunca vazio: nota sem texto ainda tem um paragrafo. */
    private String snapshot() {
        String html = htmlText();
        return html.isBlank() ? "<html><body><p></p></body></html>" : html;
    }

    /** Repoe uma foto do documento, sem que a reposicao entre na pilha de desfazer. */
    private void restoreSnapshot(String html, int caret) {
        withoutUndo(() -> {
            textPane.setText(html);
            applyTypography();
            textPane.setCaretPosition(Math.min(caret, textPane.getDocument().getLength()));
        });
    }

    /** Um passo de desfazer que troca o documento inteiro pela foto do outro lado. */
    private final class SnapshotEdit extends AbstractUndoableEdit {

        private final String antes;
        private final int caretAntes;
        private final String depois;
        private final int caretDepois;

        SnapshotEdit(String antes, int caretAntes, String depois, int caretDepois) {
            this.antes = antes;
            this.caretAntes = caretAntes;
            this.depois = depois;
            this.caretDepois = caretDepois;
        }

        @Override
        public void undo() {
            super.undo();
            restoreSnapshot(antes, caretAntes);
        }

        @Override
        public void redo() {
            super.redo();
            restoreSnapshot(depois, caretDepois);
        }
    }

    /** Roda algo que o usuario nao pediu, sem sujar a pilha de desfazer. */
    private void withoutUndo(Runnable action) {
        boolean was = recordingUndo;
        recordingUndo = false;
        closeUndoGroup();
        try {
            action.run();
        } finally {
            recordingUndo = was;
        }
    }

    public boolean canUndo() {
        return undoManager.canUndo();
    }

    public boolean canRedo() {
        return undoManager.canRedo();
    }

    public void undo() {
        closeUndoGroup();
        if (!undoManager.canUndo()) {
            return;
        }
        // o proprio desfazer mexe no documento; gravar isso empilharia o desfazer do desfazer
        withUndoSafety(() -> withoutUndo(undoManager::undo));
    }

    public void redo() {
        closeUndoGroup();
        if (!undoManager.canRedo()) {
            return;
        }
        withUndoSafety(() -> withoutUndo(undoManager::redo));
    }

    /**
     * Desfaz ou refaz com rede: se a operacao estourar no meio, o documento volta a como
     * estava antes dela e a pilha e jogada fora.
     *
     * <p>Um desfazer pela metade nao e so um texto errado na tela -- ja custou a janela
     * inteira: com a arvore de elementos inconsistente, o Swing entra em circulo calculando
     * as linhas e come a memoria da maquina. Perder o historico e o preco barato; a rede
     * existe para o caso que ainda nao conhecemos, ja que a foto do
     * {@link #asOneUndoStep(Runnable)} resolveu os que conhecemos.
     */
    private void withUndoSafety(Runnable action) {
        String rede = snapshot();
        int caret = textPane.getCaretPosition();
        try {
            action.run();
        } catch (RuntimeException e) { // inclui CannotUndoException e CannotRedoException
            System.err.println("Desfazer falhou na nota " + note.id()
                    + "; voltando ao estado anterior: " + e);
            Trace.linha("!! desfazer estourou, repondo a rede: " + e);
            restoreSnapshot(rede, caret);
            undoManager.discardAllEdits();
        }
        scheduleSave();
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
        // um sexto da linha e joga o marcador para o meio do nada. Com 10px, medido no pixel
        // pintado, o ponto sai em x=15 e o texto do item em x=23, contra x=10 do texto
        // normal. O numero e escolha do usuario; a checagem trava a medida.
        css.addRule("ul, ol { margin-top: 0; margin-bottom: 0; margin-left: 10px;"
                + " padding-left: 0; }");
        css.addRule("a { text-decoration: underline; }");
        // <tt> e a marca de trecho monoespacado. O Swing ja desenha <tt> em largura fixa; a
        // regra deixa explicito qual familia, e o mesmo texto vai para o <style> do arquivo.
        css.addRule("tt { font-family: " + MONO_FAMILY_CSS + "; }");
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
        // Conteudo solto (texto com <br>) vira um <p> por linha. Sem isto a tecla ENTER nao
        // funciona: o insert-break do Swing divide o paragrafo onde o cursor esta, e fora de
        // um <p> de verdade nao ha o que dividir -- a tecla inseria um "\n" invisivel.
        String corpo = HtmlText.toParagraphs(HtmlText.body(html));
        if (corpo.isBlank()) {
            // Nota vazia ainda e um paragrafo vazio. Sem ele o <body> nao tem onde receber
            // texto, e o que for digitado vai parar em qualquer lugar que o Swing escolher.
            corpo = "<p></p>";
        }
        String conteudo = "<html><body>" + corpo + "</body></html>";
        // Nada da abertura da nota entra na pilha de desfazer. Se entrasse, o primeiro Ctrl+Z
        // de uma nota recem-aberta apagaria o texto todo -- para o documento, carregar e
        // inserir. O discardAllEdits e cinto e suspensorio para o que o kit dispare sozinho.
        withoutUndo(() -> {
            textPane.setText(conteudo);
            textPane.setCaretPosition(bodyStart());
            applyTypography();
        });
        undoManager.discardAllEdits();
        // o ponto de partida do diario: sem o conteudo de abertura, os passos seguintes nao
        // se repetem, porque nao se sabe sobre o que eles agiram
        if (Trace.ligado()) {
            Trace.linha("ABRIU nota=" + note.id() + " " + note.width() + "x" + note.height()
                    + " conteudo=" + conteudo);
        }
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

        // ENTER dentro de uma lista cria o item seguinte; fora dela, o Swing divide o
        // paragrafo, o que passou a funcionar desde que o conteudo mora em <p> de verdade.
        Action swingBreak = textPane.getActionMap().get(DefaultEditorKit.insertBreakAction);
        bindStyle("ENTER", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!splitListItem()) {
                    swingBreak.actionPerformed(e);
                }
                scheduleSave();
            }
        });
    }

    /**
     * ENTER dentro de um item de lista. Item com texto: divide, e o que estava depois do
     * cursor vai para o item novo. Item vazio: sai da lista, que e o unico jeito de terminar
     * uma lista sem usar o mouse.
     *
     * @return {@code false} quando o cursor nao esta numa lista -- ai o ENTER e do Swing.
     */
    private boolean splitListItem() {
        if (!(textPane.getDocument() instanceof HTMLDocument doc)) {
            return false;
        }
        Element item = enclosing(doc, textPane.getCaretPosition(), HTML.Tag.LI);
        if (item == null) {
            return false;
        }
        asOneUndoStep(() -> {
            try {
                if (isEmptyItem(doc, item)) {
                    leaveList(doc, item);
                } else {
                    splitItem(doc, item);
                }
            } catch (BadLocationException | IOException e) {
                System.err.println("Nao foi possivel quebrar o item da lista: " + e.getMessage());
            }
        });
        return true;
    }

    /** O item novo leva o que estava depois do cursor. */
    private void splitItem(HTMLDocument doc, Element item) throws BadLocationException, IOException {
        int caret = textPane.getCaretPosition();
        int end = Math.min(item.getEndOffset() - 1, doc.getLength());
        String rest = end > caret ? HtmlText.body(rangeAsHtml(caret, end)) : "";
        if (end > caret) {
            doc.remove(caret, end - caret);
        }
        doc.insertAfterEnd(item, "<li>" + rest + "</li>");
        Element novo = nextSibling(item);
        if (novo != null) {
            textPane.setCaretPosition(Math.min(novo.getStartOffset(), doc.getLength()));
        }
    }

    /** Item vazio + ENTER termina a lista, com um paragrafo depois dela. */
    private void leaveList(HTMLDocument doc, Element item) throws BadLocationException, IOException {
        Element list = item.getParentElement();
        doc.insertAfterEnd(list, "<p></p>");
        doc.removeElement(item);
        int after = Math.min(list.getEndOffset(), doc.getLength());
        textPane.setCaretPosition(after);
    }

    private static boolean isEmptyItem(HTMLDocument doc, Element item) throws BadLocationException {
        int start = item.getStartOffset();
        int end = Math.min(item.getEndOffset(), doc.getLength());
        return end <= start || doc.getText(start, end - start).strip().isEmpty();
    }

    /** O elemento com esta tag que contem a posicao, ou {@code null} se nao houver. */
    private static Element enclosing(HTMLDocument doc, int offset, HTML.Tag tag) {
        for (Element at = doc.getCharacterElement(offset); at != null; at = at.getParentElement()) {
            if (tag.toString().equals(at.getName())) {
                return at;
            }
        }
        return null;
    }

    private static Element nextSibling(Element element) {
        Element parent = element.getParentElement();
        for (int i = 0; i < parent.getElementCount() - 1; i++) {
            if (parent.getElement(i) == element) {
                return parent.getElement(i + 1);
            }
        }
        return null;
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
        insertList(HTML.Tag.UL);
    }

    /** A mesma coisa, numerada: {@code <ol>} em vez de {@code <ul>}. */
    public void insertNumberedList() {
        insertList(HTML.Tag.OL);
    }

    private void insertList(HTML.Tag tag) {
        // apagar as linhas e inserir a lista tem de voltar junto: um Ctrl+Z que desfizesse
        // meia conversao deixaria a nota num estado que nunca existiu na tela
        asOneUndoStep(() -> buildList(tag));
    }

    private void buildList(HTML.Tag tag) {
        String vazia = "<" + tag + "><li></li></" + tag + ">";
        if (!(textPane.getDocument() instanceof HTMLDocument doc)) {
            return;
        }
        int selectionStart = textPane.getSelectionStart();
        int selectionEnd = textPane.getSelectionEnd();
        if (selectionEnd <= selectionStart) {
            insertHtml(vazia, tag);
            return;
        }
        try {
            int from = lineStart(doc, skipEmptyLead(doc, selectionStart, selectionEnd));
            int to = lineEnd(doc, Math.max(from, selectionEnd - 1));
            List<String> items = HtmlText.lines(HtmlText.body(rangeAsHtml(from, to)));
            if (items.isEmpty()) {
                insertHtml(vazia, tag);
                return;
            }
            StringBuilder list = new StringBuilder("<" + tag + ">");
            for (String item : items) {
                list.append("<li>").append(item).append("</li>");
            }
            list.append("</").append(tag).append(">");

            replaceRange(doc, from, to, list.toString(), tag, true);
        } catch (BadLocationException | IOException e) {
            System.err.println("Nao foi possivel transformar a selecao em lista: "
                    + e.getMessage());
        }
    }

    /**
     * Troca um trecho do documento por HTML.
     *
     * @param eatBreaks quando o HTML novo e um bloco (uma lista, por exemplo): ele mesmo
     *                  separa o que vem antes e depois, e as quebras que delimitavam o trecho
     *                  virariam linha em branco se ficassem. Para texto comum, {@code false}:
     *                  ali as quebras sao as linhas.
     */
    private void replaceRange(HTMLDocument doc, int from, int to, String html, HTML.Tag tag,
            boolean eatBreaks) throws BadLocationException, IOException {
        int removeFrom = eatBreaks && from > 1 && isBreak(doc, from - 1) ? from - 1 : from;
        int removeTo = eatBreaks && to < doc.getLength() && isBreak(doc, to) ? to + 1 : to;

        doc.remove(removeFrom, removeTo - removeFrom);

        // Se o trecho era um paragrafo inteiro -- e o que acontece com texto colado de fora,
        // que chega como um <p> por linha --, sobra um paragrafo vazio no lugar. Trocar o
        // elemento nao deixa esse resto; inserir no cursor deixaria uma linha em branco.
        Element paragraph = doc.getParagraphElement(removeFrom);
        if (paragraph.getEndOffset() - paragraph.getStartOffset() <= 1) {
            doc.setOuterHTML(paragraph, html);
            scheduleSave();
            return;
        }
        textPane.setCaretPosition(removeFrom);
        applyStyle(new HTMLEditorKit.InsertHTMLTextAction("troca", html, HTML.Tag.BODY, tag));
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
        String rotulo = label == null || label.isBlank() ? url : label;

        // apagar a selecao e inserir a ancora e um passo so para o Ctrl+Z: o meio do caminho
        // -- a nota com a palavra apagada e sem o link ainda -- ninguem viu nem quis
        asOneUndoStep(() -> {
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
                    + HtmlText.escapeHtml(rotulo) + "</a>", HTML.Tag.A);
        });
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

    /**
     * A primeira posicao <b>dentro do corpo</b> do documento.
     *
     * <p>O offset 0 nao serve: num HTMLDocument ele pertence ao {@code <head>}, que tem um
     * paragrafo implicito proprio. Deixar o cursor ali fazia o texto digitado numa nota nova
     * entrar no cabecalho em vez do corpo -- e dali em diante nada funcionava: a lista nao
     * achava paragrafos, o ENTER nao dividia nada, e o documento ficava numa forma que
     * chegou a travar o layout do Swing.
     */
    private int bodyStart() {
        Document doc = textPane.getDocument();
        if (doc instanceof HTMLDocument html) {
            Element body = childNamed(html.getDefaultRootElement(), HTML.Tag.BODY);
            if (body != null) {
                return Math.min(body.getStartOffset(), doc.getLength());
            }
        }
        return Math.min(1, doc.getLength());
    }

    private static Element childNamed(Element parent, HTML.Tag tag) {
        for (int i = 0; i < parent.getElementCount(); i++) {
            if (tag.toString().equals(parent.getElement(i).getName())) {
                return parent.getElement(i);
            }
        }
        return null;
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
        textPane.getActionMap().put(name, traced(keyStroke, action));
    }

    /** A mesma embalagem do diario de bordo, para as acoes do editor. */
    private Action traced(String nome, Action action) {
        if (!Trace.ligado()) {
            return action;
        }
        return new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Trace.comando("tecla " + nome, cursorState(),
                        () -> action.actionPerformed(e), NoteFrame.this::htmlText);
            }
        };
    }

    /** Dispara uma acao de estilo como se tivesse vindo do teclado. */
    private void applyStyle(Action action) {
        action.actionPerformed(new ActionEvent(textPane, ActionEvent.ACTION_PERFORMED, ""));
        scheduleSave();
    }

    /**
     * Limpa a formatacao da selecao -- ou da nota toda, se nao houver selecao. Tira negrito,
     * italico e sublinhado, e <b>desmonta as listas</b>: item de lista volta a ser linha de
     * texto comum. O link fica: link nao e decoracao, e conteudo, e perder o endereco aqui
     * seria perder informacao que nao esta em nenhum outro lugar.
     */
    public void clearFormatting() {
        asOneUndoStep(() -> {
            clearCharacterStyles();
            unwrapBlocks();
        });
    }

    /** Se a barra de rolagem esta com a nossa pintura, e nao com a do sistema. */
    public boolean scrollBarStyled() {
        return scrollBar != null && scrollBar.getUI() instanceof NoteScrollBarUI
                && scrollBar.getPreferredSize().width == SCROLLBAR_WIDTH;
    }

    /** A cor de fundo da barra de rolagem, que segue a paleta da nota. */
    public Color scrollBarBackground() {
        return scrollBar == null ? null : scrollBar.getBackground();
    }

    /** Se o cursor esta dentro de um item de lista. */
    public boolean caretInsideList() {
        return textPane.getDocument() instanceof HTMLDocument doc
                && enclosing(doc, textPane.getCaretPosition(), HTML.Tag.LI) != null;
    }

    /** O documento em HTML, o mesmo que vai para o disco. Publico para as checagens. */
    public String htmlAtual() {
        return htmlText();
    }

    /** Liga e desliga o negrito, como o botao B. Publico para as checagens. */
    public void applyBold() {
        applyStyle(new StyledEditorKit.BoldAction());
    }

    /**
     * Insere texto no cursor, como o teclado faria. Existe para as checagens conseguirem
     * reproduzir uma sessao de digitacao de verdade -- foi digitando numa nota nova que o
     * texto ia para o cabecalho do documento.
     */
    public void type(String text) {
        textPane.replaceSelection(text);
    }

    /** A tecla ENTER, sem passar pelo teclado. Para as checagens exercitarem a quebra. */
    public void pressEnter() {
        Action enter = textPane.getActionMap().get("recados-estilo:ENTER");
        if (enter != null) {
            enter.actionPerformed(new ActionEvent(textPane, ActionEvent.ACTION_PERFORMED, ""));
        }
    }

    private void clearCharacterStyles() {
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
    }

    /**
     * As listas voltam a ser linhas de texto. Sem selecao, vale a nota toda -- ai o documento
     * e remontado de uma vez, que e mais simples e mais seguro do que mexer elemento por
     * elemento. Com selecao, cada <b>lista tocada</b> e trocada por inteiro.
     *
     * <p>Trocar a lista inteira, e nao o trecho selecionado, nao e preguica: remover o texto
     * dos itens deixa o {@code <ul>} e os {@code <li>} vazios de pe, e o texto novo acaba
     * <i>dentro</i> do item -- o marcador do primeiro item continuava na tela e as linhas
     * saiam indentadas. Foi o que aconteceu na primeira versao disto.
     *
     * <p>So lista entra aqui. Paragrafo nao precisa ser desmontado (ja e uma linha), e
     * desmontar o paragrafo inteiro tiraria o negrito de fora da selecao tambem.
     */
    private void unwrapBlocks() {
        if (!(textPane.getDocument() instanceof HTMLDocument doc)) {
            return;
        }
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        try {
            if (start == end) {
                String flat = HtmlText.flatten(HtmlText.body(htmlText()));
                textPane.setText("<html><body>"
                        + HtmlText.toParagraphs(flat) + "</body></html>");
                textPane.setCaretPosition(0);
                applyTypography();
                return;
            }
            // de tras para frente: trocar uma lista mexe nos offsets do que vem depois dela
            List<Element> lists = listsIn(doc, start, end);
            for (int i = lists.size() - 1; i >= 0; i--) {
                unwrapList(doc, lists.get(i));
            }
        } catch (BadLocationException | IOException e) {
            System.err.println("Nao foi possivel limpar a formatacao: " + e.getMessage());
        }
    }

    private void unwrapList(HTMLDocument doc, Element list) throws BadLocationException, IOException {
        int from = list.getStartOffset();
        int to = Math.min(list.getEndOffset(), doc.getLength());
        String flat = HtmlText.flatten(HtmlText.body(rangeAsHtml(from, to)));
        if (!flat.isBlank()) {
            doc.setOuterHTML(list, HtmlText.toParagraphs(flat));
        }
    }

    /** As listas que o trecho toca, das de fora para dentro, sem repetir lista aninhada. */
    private static List<Element> listsIn(HTMLDocument doc, int start, int end) {
        List<Element> found = new ArrayList<>();
        collectLists(doc.getDefaultRootElement(), start, end, found);
        return found;
    }

    private static void collectLists(Element element, int start, int end, List<Element> found) {
        for (int i = 0; i < element.getElementCount(); i++) {
            Element child = element.getElement(i);
            if (child.getEndOffset() <= start || child.getStartOffset() >= end) {
                continue;
            }
            String name = child.getName();
            if (HTML.Tag.UL.toString().equals(name) || HTML.Tag.OL.toString().equals(name)) {
                found.add(child); // sem descer: a lista de dentro sai junto com a de fora
            } else {
                collectLists(child, start, end, found);
            }
        }
    }

    /**
     * Cor, fonte e tamanho vem da <b>nota</b>, e nao do documento. Sem reaplicar aqui, o HTML
     * traz de volta o que estava gravado e trocar a cor (ou a fonte, ou o tamanho) deixava o
     * texto como estava antes. Os links ficam com o azul de link: nao e decoracao, e sinal de
     * que da para clicar.
     *
     * <p>Por atributo de caractere, e nao por regra de CSS: regra adicionada a folha de
     * estilo de um documento que <b>ja existe</b> nao redesenha nada -- medido. Atributo
     * redesenha na hora, e e o mesmo caminho que a cor da paleta sempre usou.
     */
    private void applyTypography() {
        Color color = note.palette().text();
        textPane.setForeground(color);
        textPane.setCaretColor(color);
        StyledDocument doc = textPane.getStyledDocument();
        if (doc.getLength() == 0) {
            return;
        }
        // Cor e tamanho, e nao a familia: quem marca trecho monoespacado e a tag <tt>, e
        // estampar familia em todos os trechos apagaria essa marca a cada recoloracao.
        SimpleAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, color);
        StyleConstants.setFontSize(attrs, note.fontSize());
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

    /**
     * A barra de rolagem pintada com a cor da nota: trilha da cor do corpo, e o polegar um
     * cinza translucido que funciona sobre qualquer paleta. Sem setas nas pontas -- em nota
     * pequena elas comem a barra toda, e ninguem rola uma nota clicando em seta.
     */
    private final class NoteScrollBarUI extends BasicScrollBarUI {

        @Override
        protected void configureScrollBarColors() {
            trackColor = note.palette().body();
            thumbColor = note.palette().header();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return invisibleButton();
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return invisibleButton();
        }

        private JButton invisibleButton() {
            JButton button = new JButton();
            Dimension none = new Dimension(0, 0);
            button.setPreferredSize(none);
            button.setMinimumSize(none);
            button.setMaximumSize(none);
            button.setFocusable(false);
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent component, Rectangle bounds) {
            g.setColor(note.palette().body());
            g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent component, Rectangle bounds) {
            if (bounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillRoundRect(bounds.x + 2, bounds.y + 1,
                    bounds.width - 4, bounds.height - 2, 5, 5);
            g2.dispose();
        }
    }

    /** Os desenhos dos botoes: os quatro primeiros da barra de titulo, o resto do rodape. */
    private enum Glyph {
        PLUS, COLOR, PIN_ON, PIN_OFF, MINIMIZE,
        BOLD, ITALIC, UNDERLINE, MONOSPACED, LIST, NUMBERED_LIST, LINK, ERASER
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

        /**
         * Os da barra de formatacao sao menores que os da barra de titulo, porque sao oito e
         * tem de caber na nota mais estreita ao lado da alca: 7x18 mais 22 do "</>" mais 14
         * da alca mais 2 da borda dao 164, e a nota minima tem 180. Ha checagem: o nono botao
         * vai avisar que nao cabe, como o oitavo avisou.
         */
        private static final int SMALL_SIZE = 18;

        private Glyph glyph;
        private final Runnable action;
        private boolean hovered;

        GlyphButton(Glyph glyph, String tooltip, Runnable action) {
            this(glyph, tooltip, action, SIZE);
        }

        GlyphButton(Glyph glyph, String tooltip, Runnable action, int size) {
            this.glyph = glyph;
            this.action = action;
            // Nao pode receber o foco: botao que rouba o foco do editor apaga a selecao na
            // tela, e quem clica no B depois de marcar uma palavra perde de vista o que
            // marcou. Assim o clique executa a acao e o cursor de texto fica onde estava.
            setFocusable(false);
            setPreferredSize(new Dimension(size, size));
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

                // "</>" -- o sinal de codigo. Simbolo, e nao letra: as tres letras ao lado
                // (B, I, U) ja ocupam essa forma, e um quarto caractere se perderia na fila.
                // Este usa a largura toda do botao, e nao a caixa de 10px dos outros: tres
                // elementos (seta, barra, seta) em 10px se encostam e viram borrao -- foi o
                // que a primeira versao mostrou quando desenhei a barra num bitmap para ver.
                case MONOSPACED -> {
                    int largura = getWidth() - 4;
                    int inicio = 2;
                    int fim = inicio + largura;
                    int meio = y + box / 2;
                    int topo = y + 1;
                    int base = y + box - 1;
                    g2.drawLine(inicio + 4, topo, inicio, meio);
                    g2.drawLine(inicio, meio, inicio + 4, base);
                    g2.drawLine(fim - 4, topo, fim, meio);
                    g2.drawLine(fim, meio, fim - 4, base);
                    // quase vertical: com mais inclinacao o topo da barra encosta na seta
                    // da direita, e a base encosta na da esquerda
                    g2.drawLine(inicio + largura / 2 + 1, topo,
                            inicio + largura / 2 - 1, base);
                }

                case LIST -> {
                    for (int row = 0; row < 3; row++) {
                        int ly = y + row * 4 + 1;
                        g2.fillRect(x, ly, 2, 2);
                        g2.drawLine(x + 4, ly + 1, x + box, ly + 1);
                    }
                }
                // as mesmas tres linhas da lista, com algarismo no lugar do ponto
                case NUMBERED_LIST -> {
                    g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 7));
                    for (int row = 0; row < 3; row++) {
                        int ly = y + row * 4 + 1;
                        g2.drawString(Integer.toString(row + 1), x - 1, ly + 4);
                        g2.drawLine(x + 5, ly + 1, x + box, ly + 1);
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
        bind("control Z", this::undo);
        bind("control Y", this::redo);
        bind("control shift Z", this::redo);
        bind("control N", () -> host.newNote(this));
        bind("control W", () -> host.closeNote(this));
        bind("control D", () -> host.deleteNote(this));
        bind("control E", this::cycleColor);
        // O "+" do teclado principal chega como shift no "=", e o do teclado numerico como
        // ADD: os dois precisam estar aqui, senao a tecla funciona so num lado do teclado.
        bind("control PLUS", this::zoomIn);
        bind("control ADD", this::zoomIn);
        bind("control EQUALS", this::zoomIn);
        bind("control shift EQUALS", this::zoomIn);
        bind("control MINUS", this::zoomOut);
        bind("control SUBTRACT", this::zoomOut);
        bind("control 0", this::resetZoom);
        bind("control T", this::togglePin);
        bind("control COMMA", () -> host.openSettings(this));
        bind("control shift A", host::showAll);
        bind("control Q", host::quit);
    }

    private void bind(String keyStroke, Runnable action) {
        KeyStroke stroke = KeyStroke.getKeyStroke(keyStroke);
        if (stroke == null) {
            // nome de tecla que o Swing nao reconhece: melhor dizer alto do que perder o
            // atalho em silencio (foi o risco ao cobrir as varias formas do "+")
            System.err.println("Atalho ignorado, tecla desconhecida: " + keyStroke);
            return;
        }
        String name = "recados:" + keyStroke;
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, name);
        getRootPane().getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                traced("tecla " + keyStroke, action).run();
            }
        });
    }

    /**
     * Embrulha uma acao no diario de bordo: o que foi acionado, com o cursor onde, e como o
     * HTML ficou depois. Sem {@code -Drecados.trace} isto e a propria acao, sem desvio.
     *
     * <p>Vale a pena passar <i>todas</i> as acoes por aqui, e nao so as suspeitas: o
     * travamento que estamos atras aparece depois de uma sequencia de passos, e um diario
     * com buracos nao permite repetir a sequencia.
     */
    private Runnable traced(String nome, Runnable acao) {
        if (!Trace.ligado()) {
            return acao;
        }
        return () -> Trace.comando(nome, cursorState(), acao, this::htmlText);
    }

    /** Onde esta o cursor e o que esta selecionado, para o diario. */
    private String cursorState() {
        Document doc = textPane.getDocument();
        int inicio = textPane.getSelectionStart();
        int fim = textPane.getSelectionEnd();
        String sel = inicio == fim ? "" : " sel=[" + inicio + "," + fim + ")";
        return "nota=" + note.id() + " cursor=" + textPane.getCaretPosition() + sel
                + " tamanho=" + doc.getLength();
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
        // Recolorir e consequencia de trocar a cor da nota, nao uma edicao do texto. Se
        // entrasse na pilha, o Ctrl+Z devolveria a cor antiga so no documento, e a nota
        // continuaria gravada com a cor nova -- os dois discordando.
        withoutUndo(this::applyTypography);
        paintForeground(header, palette.text());
        paintForeground(footer, palette.text()); // os botoes de formatacao tambem
        if (scrollBar != null) {
            scrollBar.setBackground(palette.body());
            scrollBar.repaint(); // a trilha le a paleta na hora de pintar
        }
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

    /**
     * Ctrl+ e Ctrl-: muda o tamanho da fonte <b>e a janela na mesma proporcao</b>, para o
     * texto continuar ocupando o mesmo espaco relativo dentro da nota.
     */
    public void zoomIn() {
        zoom(+1);
    }

    public void zoomOut() {
        zoom(-1);
    }

    public void resetZoom() {
        applyFontSize(Note.DEFAULT_FONT_SIZE);
    }

    private void zoom(int direction) {
        int index = nearestSize(note.fontSize());
        applyFontSize(FONT_SIZES[Math.max(0, Math.min(FONT_SIZES.length - 1, index + direction))]);
    }

    private static int nearestSize(int size) {
        int best = 0;
        for (int i = 1; i < FONT_SIZES.length; i++) {
            if (Math.abs(FONT_SIZES[i] - size) < Math.abs(FONT_SIZES[best] - size)) {
                best = i;
            }
        }
        return best;
    }

    private void applyFontSize(int size) {
        int previous = note.fontSize();
        if (size == previous) {
            return;
        }
        double ratio = (double) size / previous;
        note.fontSize(size);
        // A nota primeiro, a janela depois: o guarda do componentResized desfaz qualquer
        // tamanho que nao seja o da nota, entao mudar a nota antes torna isto legitimo.
        note.size((int) Math.round(note.width() * ratio),
                (int) Math.round(note.height() * ratio));
        setSize(note.width(), note.height());
        applyTypography();
        flush(); // escolha explicita do usuario: grava na hora
    }

    /**
     * Liga e desliga a fonte de largura fixa <b>do trecho selecionado</b> -- sem selecao,
     * da nota toda. E marcada com {@code <tt>}, e nao com atributo de fonte no trecho: por
     * atributo o Swing desenha certo na tela mas o escritor grava {@code face=""}, e a fonte
     * se perde no disco (medido). O {@code <tt>} sobrevive ao ida-e-volta e o navegador ja o
     * entende como monoespacado.
     *
     * <p>Trabalha paragrafo por paragrafo porque {@code <tt>} e inline: envolver um trecho que
     * atravessa paragrafos daria {@code <tt>a</p><p>b</tt>}, que nao e HTML.
     */
    public void toggleMonospaced() {
        if (!(textPane.getDocument() instanceof HTMLDocument doc)) {
            return;
        }
        int selectionStart = textPane.getSelectionStart();
        int selectionEnd = textPane.getSelectionEnd();
        int from = selectionStart == selectionEnd ? 0 : selectionStart;
        int to = selectionStart == selectionEnd ? doc.getLength() : selectionEnd;
        if (to <= from) {
            return;
        }
        boolean turningOff = isMonospaced(doc, from);
        asOneUndoStep(() -> {
            try {
                List<Element> paragraphs = paragraphsIn(doc, from, to);
                // de tras para frente: remontar um paragrafo mexe nos offsets dos seguintes
                for (int i = paragraphs.size() - 1; i >= 0; i--) {
                    remarkParagraph(doc, paragraphs.get(i), from, to, !turningOff);
                }
            } catch (BadLocationException | IOException e) {
                System.err.println("Nao foi possivel trocar a fonte do trecho: " + e.getMessage());
            }
        });
        // Remontar o paragrafo derruba a selecao, e sem ela um segundo clique no botao nao
        // desfaria o mesmo trecho. Remarcar e seguro: a marcacao mudou, o texto nao, entao os
        // offsets continuam valendo.
        if (selectionStart != selectionEnd) {
            textPane.select(from, Math.min(to, textPane.getDocument().getLength()));
        }
        flush();
    }

    /**
     * Remonta um paragrafo com a parte selecionada marcada (ou desmarcada) como monoespacada.
     *
     * <p>O paragrafo e reconstruido inteiro, de uma vez, em vez de trocar so o trecho: a
     * insercao de HTML do Swing trata {@code <tt>} como bloco e <b>parte o paragrafo em
     * tres</b> -- "antes MEIO depois" virava tres paragrafos. Remontando, o paragrafo continua
     * um.
     */
    private void remarkParagraph(HTMLDocument doc, Element paragraph, int from, int to,
            boolean monospaced) throws BadLocationException, IOException {
        int start = paragraph.getStartOffset();
        int end = Math.min(paragraph.getEndOffset() - 1, doc.getLength());
        int a = Math.max(from, start);
        int b = Math.min(to, end);
        if (b <= a) {
            return;
        }
        String before = a > start ? HtmlText.inline(rangeAsHtml(start, a)) : "";
        String middle = HtmlText.withoutMonospace(HtmlText.inline(rangeAsHtml(a, b)));
        String after = end > b ? HtmlText.inline(rangeAsHtml(b, end)) : "";
        if (middle.isBlank()) {
            return;
        }
        doc.setInnerHTML(paragraph,
                before + (monospaced ? "<tt>" + middle + "</tt>" : middle) + after);
    }

    /** Se o cursor (ou o comeco da selecao) esta num trecho monoespacado. */
    public boolean monospacedAtCaret() {
        return textPane.getDocument() instanceof HTMLDocument doc
                && isMonospaced(doc, textPane.getSelectionStart());
    }

    /**
     * O Swing guarda {@code <tt>} como <b>atributo do caractere</b>, e nao como elemento na
     * arvore -- como faz com {@code <b>} e {@code <a>}. Procurar por elemento aqui nunca acha
     * nada, e foi assim que desligar a fonte nao funcionou na primeira versao.
     */
    private static boolean isMonospaced(HTMLDocument doc, int offset) {
        int at = Math.min(Math.max(offset, 1), Math.max(doc.getLength() - 1, 0));
        return doc.getCharacterElement(at).getAttributes().getAttribute(HTML.Tag.TT) != null;
    }

    /** Os paragrafos que o trecho toca. */
    private static List<Element> paragraphsIn(HTMLDocument doc, int start, int end) {
        List<Element> found = new ArrayList<>();
        for (int at = start; at <= end; ) {
            Element paragraph = doc.getParagraphElement(at);
            if (found.isEmpty() || found.get(found.size() - 1) != paragraph) {
                found.add(paragraph);
            }
            int next = paragraph.getEndOffset();
            at = next > at ? next : at + 1;
        }
        return found;
    }

    /**
     * Troca um trecho por HTML <b>inline</b>, sem mexer no bloco em volta. Diferente de
     * {@link #replaceRange}: la, paragrafo que esvazia e substituido pelo HTML novo (a lista
     * ocupa o lugar dele); aqui o paragrafo tem de continuar existindo, com o texto dentro.
     */
    private void replaceInline(HTMLDocument doc, int from, int to, String html, HTML.Tag tag)
            throws BadLocationException {
        doc.remove(from, to - from);
        textPane.setCaretPosition(from);
        applyStyle(new HTMLEditorKit.InsertHTMLTextAction("trecho", html, HTML.Tag.BODY, tag));
    }

    public void cycleColor() {
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
        // desligado quando nao ha o que desfazer, para o menu dizer a verdade
        closeUndoGroup();
        menu.add(enabled(item("Desfazer (Ctrl+Z)", this::undo), canUndo()));
        menu.add(enabled(item("Refazer (Ctrl+Y)", this::redo), canRedo()));
        menu.addSeparator();
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
        menu.add(item("Inserir lista numerada", this::insertNumberedList));
        menu.add(item("Inserir link...", this::promptLink));
        menu.addSeparator();
        // marcada quando o cursor (ou o comeco da selecao) esta num trecho monoespacado
        JCheckBoxMenuItem mono = new JCheckBoxMenuItem("Fonte monoespacada (na selecao)");
        mono.setSelected(monospacedAtCaret());
        mono.addActionListener(e -> toggleMonospaced());
        menu.add(mono);
        menu.add(item("Aumentar o texto e a nota (Ctrl +)", this::zoomIn));
        menu.add(item("Diminuir o texto e a nota (Ctrl -)", this::zoomOut));
        menu.add(item("Tamanho normal (Ctrl 0)", this::resetZoom));
        menu.addSeparator();
        menu.add(item("Copiar tudo (com formatacao)", this::copyAll));
        menu.addSeparator();
        menu.add(item("Limpar formatacao", this::clearFormatting));
        return menu;
    }

    private static JMenuItem enabled(JMenuItem item, boolean enabled) {
        item.setEnabled(enabled);
        return item;
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
        // a gravacao e o unico momento em que o HTML aparece depois de uma digitacao comum
        // (que nao passa por comando nenhum) -- sem esta linha o diario pula esse trecho
        if (Trace.ligado()) {
            Trace.linha("SALVOU " + cursorState() + " html="
                    + note.html().replace("\r", "").replace("\n", "\\n"));
        }
        if (host.saveNote(note)) {
            failedSaves = 0;
            return;
        }
        // Falha de gravacao aqui e quase sempre passageira -- algum processo com o arquivo
        // aberto por um instante --, entao a resposta certa e tentar de novo, nao desistir do
        // que o usuario escreveu. O limite existe para o caso de a falha ser permanente
        // (disco cheio, pasta sem permissao): ai insistir a cada meio segundo para sempre so
        // enche o log. A proxima tecla recomeca a contagem.
        if (++failedSaves <= SAVE_RETRIES) {
            System.err.println("Nova tentativa de gravar a nota " + note.id()
                    + " em " + SAVE_DELAY_MS + "ms (" + failedSaves + "/" + SAVE_RETRIES + ")");
            saveTimer.restart();
        } else {
            System.err.println("Desistindo de gravar a nota " + note.id()
                    + " por agora; a proxima edicao tenta de novo.");
        }
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
