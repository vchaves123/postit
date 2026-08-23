package com.recados;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Icones desenhados em runtime -- evita carregar recursos binarios no jar. */
public final class Icons {

    private Icons() {
    }

    /** Uma notinha adesiva com a ponta dobrada, para a bandeja do sistema. */
    public static BufferedImage trayIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int pad = Math.max(1, size / 8);
        int side = size - 2 * pad;
        int fold = Math.max(3, side / 3);

        Palette yellow = Palette.at(0);
        g.setColor(yellow.body());
        g.fillRect(pad, pad, side, side);

        // ponta dobrada no canto inferior direito
        g.setColor(yellow.header().darker());
        int[] xs = {pad + side - fold, pad + side, pad + side};
        int[] ys = {pad + side, pad + side - fold, pad + side};
        g.fillPolygon(xs, ys, 3);

        g.setColor(new Color(0, 0, 0, 90));
        g.drawRect(pad, pad, side - 1, side - 1);

        // duas "linhas de texto"
        g.setColor(new Color(0, 0, 0, 110));
        int lineX = pad + side / 5;
        int lineW = side - 2 * (side / 5);
        g.fillRect(lineX, pad + side / 3, lineW, Math.max(1, size / 16));
        g.fillRect(lineX, pad + side / 2, lineW / 2, Math.max(1, size / 16));

        g.dispose();
        return image;
    }
}
