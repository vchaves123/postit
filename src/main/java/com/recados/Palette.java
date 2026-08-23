package com.recados;

import java.awt.Color;
import java.util.List;

/**
 * As cores disponiveis para as notas. O indice guardado na nota e' a posicao em {@link #ALL}.
 */
public record Palette(String name, Color body, Color header, Color text) {

    public static final List<Palette> ALL = List.of(
            new Palette("Amarelo", new Color(0xFFF9A8), new Color(0xFCEF6E), new Color(0x3D3A1E)),
            new Palette("Rosa",    new Color(0xFFC9DD), new Color(0xFFA8C7), new Color(0x452430)),
            new Palette("Verde",   new Color(0xC9F5B9), new Color(0xA6ED92), new Color(0x24401C)),
            new Palette("Azul",    new Color(0xC2E4FF), new Color(0x9AD3FF), new Color(0x1E3547)),
            new Palette("Laranja", new Color(0xFFDCAF), new Color(0xFFC178), new Color(0x452F14)),
            new Palette("Lilas",   new Color(0xE2CFFF), new Color(0xCCB0FF), new Color(0x2F2447)));

    public static Palette at(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    public static int next(int index) {
        return Math.floorMod(index + 1, ALL.size());
    }
}
