package com.recados;

import java.awt.Color;
import java.util.List;

/**
 * As cores disponiveis para as notas. O indice guardado na nota e' a posicao em {@link #ALL}.
 */
public record Palette(String name, Color body, Color header, Color text) {

    /**
     * A primeira e a cor padrao de nota nova, e a que o icone do aplicativo usa. Azul, e nao
     * amarelo, para o Recados nao ter a cara de um produto de marca: o amarelo aplicado sobre
     * a superficie inteira de uma nota adesiva e marca registrada de terceiros.
     *
     * <p>A ordem daqui nao afeta nota nenhuma ja gravada -- o arquivo guarda o nome da cor,
     * nao a posicao.
     */
    public static final List<Palette> ALL = List.of(
            new Palette("Azul",    new Color(0xC2E4FF), new Color(0x9AD3FF), new Color(0x1E3547)),
            new Palette("Verde",   new Color(0xC9F5B9), new Color(0xA6ED92), new Color(0x24401C)),
            new Palette("Rosa",    new Color(0xFFC9DD), new Color(0xFFA8C7), new Color(0x452430)),
            new Palette("Lilas",   new Color(0xE2CFFF), new Color(0xCCB0FF), new Color(0x2F2447)),
            new Palette("Laranja", new Color(0xFFDCAF), new Color(0xFFC178), new Color(0x452F14)),
            new Palette("Amarelo", new Color(0xFFF9A8), new Color(0xFCEF6E), new Color(0x3D3A1E)));

    public static Palette at(int index) {
        return ALL.get(Math.floorMod(index, ALL.size()));
    }

    /** A posicao de uma cor pelo nome, ou -1 se nao existir mais. */
    public static int indexOf(String name) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    public static int next(int index) {
        return Math.floorMod(index + 1, ALL.size());
    }
}
