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
    private int x;
    private int y;
    private int width;
    private int height;
    private int colorIndex;
    private boolean alwaysOnTop;

    public Note(String id, long createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.text = "";
        this.width = DEFAULT_WIDTH;
        this.height = DEFAULT_HEIGHT;
        this.alwaysOnTop = true;
    }

    public static Note create() {
        return new Note(UUID.randomUUID().toString(), System.currentTimeMillis());
    }

    public String id() { return id; }
    public long createdAt() { return createdAt; }

    public String text() { return text; }
    public void text(String text) { this.text = text == null ? "" : text; }

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
