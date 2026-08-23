package com.recados;

import java.util.UUID;

/**
 * Uma nota: o texto mais a geometria e a aparencia da janela que a mostra.
 * Mutavel de proposito -- a janela escreve aqui e o {@link NoteStore} persiste.
 */
public final class Note {

    public static final int DEFAULT_WIDTH = 280;
    public static final int DEFAULT_HEIGHT = 260;

    private final String id;
    private final long createdAt;
    private String text;
    private String rtf;
    private String html;
    private int x;
    private int y;
    private int width;
    private int height;
    private int colorIndex;
    private boolean alwaysOnTop;
    private boolean visible;

    public Note(String id, long createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.text = "";
        this.rtf = "";
        this.html = "";
        this.width = DEFAULT_WIDTH;
        this.height = DEFAULT_HEIGHT;
        this.alwaysOnTop = true;
        this.visible = true;
    }

    public static Note create() {
        return new Note(UUID.randomUUID().toString(), System.currentTimeMillis());
    }

    public String id() { return id; }
    public long createdAt() { return createdAt; }

    public String text() { return text; }
    public void text(String text) { this.text = text == null ? "" : text; }

    /**
     * O mesmo conteudo em HTML, com formatacao, listas e links. Gravado junto com o
     * {@link #text()}, que mantem o arquivo legivel e pesquisavel e alimenta a bandeja.
     */
    public String html() { return html; }
    public void html(String html) { this.html = html == null ? "" : html; }

    /**
     * Formatacao em RTF, de notas gravadas antes da mudanca para HTML. Somente leitura na
     * pratica: ao gravar de novo, a nota passa a usar {@link #html()}.
     */
    public String rtf() { return rtf; }
    public void rtf(String rtf) { this.rtf = rtf == null ? "" : rtf; }

    public int x() { return x; }
    public int y() { return y; }
    public int width() { return width; }
    public int height() { return height; }

    public void location(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void size(int width, int height) {
        this.width = Math.max(160, width);
        this.height = Math.max(120, height);
    }

    public int colorIndex() { return colorIndex; }
    public void colorIndex(int colorIndex) { this.colorIndex = colorIndex; }
    public Palette palette() { return Palette.at(colorIndex); }

    public boolean alwaysOnTop() { return alwaysOnTop; }
    public void alwaysOnTop(boolean alwaysOnTop) { this.alwaysOnTop = alwaysOnTop; }

    /**
     * Se a janela da nota esta aberta. Fechar uma nota nao a apaga: ela continua no disco
     * e na lista da bandeja, e volta quando o usuario mandar mostrar de novo.
     */
    public boolean visible() { return visible; }
    public void visible(boolean visible) { this.visible = visible; }

    /** Primeira linha nao vazia, usada nos menus. */
    public String title() {
        for (String line : text.split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                return trimmed.length() > 32 ? trimmed.substring(0, 31) + "…" : trimmed;
            }
        }
        return "(nota vazia)";
    }
}
