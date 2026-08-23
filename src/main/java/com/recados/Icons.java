package com.recados;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;

/** Icones desenhados em runtime -- evita carregar recursos binarios no jar. */
public final class Icons {

    /**
     * Tamanhos que o Windows procura: barra de tarefas, Alt+Tab e a janela em varios niveis
     * de escala. Entregar a lista pronta evita o sistema reescalar um unico tamanho -- e uma
     * janela sem icone nenhum cai no cafezinho padrao do Java.
     */
    private static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128};

    private Icons() {
    }

    /** Tamanhos do .ico. O 256 e o que o Explorer usa nas visualizacoes grandes. */
    private static final int[] ICO_SIZES = {16, 24, 32, 48, 64, 128, 256};

    /**
     * Gera {@code recados.ico} sem depender de ferramenta externa. O Windows escolhe o icone
     * da barra de tarefas pelo executavel que lancou o processo, nao pela janela -- rodando
     * por {@code javaw.exe} o botao mostra o cafezinho do Java, por mais icones que a janela
     * declare. Este arquivo e o que da um icone proprio ao atalho e ao exe do jpackage.
     *
     * <p>Uso: {@code java -cp recados.jar com.recados.Icons target/recados.ico}
     */
    public static void writeIco(Path target) throws IOException {
        List<byte[]> pngs = new ArrayList<>(ICO_SIZES.length);
        for (int size : ICO_SIZES) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(trayIcon(size), "png", png);
            pngs.add(png.toByteArray());
        }

        ByteArrayOutputStream ico = new ByteArrayOutputStream();
        // ICONDIR: reservado, tipo 1 (icone), quantidade
        writeShort(ico, 0);
        writeShort(ico, 1);
        writeShort(ico, ICO_SIZES.length);

        int offset = 6 + 16 * ICO_SIZES.length;
        for (int i = 0; i < ICO_SIZES.length; i++) {
            int size = ICO_SIZES[i];
            ico.write(size >= 256 ? 0 : size); // 0 significa 256
            ico.write(size >= 256 ? 0 : size);
            ico.write(0); // paleta: nenhuma
            ico.write(0); // reservado
            writeShort(ico, 1);  // planos
            writeShort(ico, 32); // bits por pixel
            writeInt(ico, pngs.get(i).length);
            writeInt(ico, offset);
            offset += pngs.get(i).length;
        }
        for (byte[] png : pngs) {
            ico.write(png);
        }
        Files.write(target, ico.toByteArray());
    }

    public static void main(String[] args) throws IOException {
        Path target = Path.of(args.length > 0 ? args[0] : "recados.ico");
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        writeIco(target);
        System.out.println("Icone gravado em " + target.toAbsolutePath());
    }

    /** Little-endian, que e o que o formato ICO usa. */
    private static void writeShort(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        writeShort(out, value & 0xFFFF);
        writeShort(out, (value >>> 16) & 0xFFFF);
    }

    public static List<Image> appIcons() {
        List<Image> icons = new ArrayList<>(SIZES.length);
        for (int size : SIZES) {
            icons.add(trayIcon(size));
        }
        return icons;
    }

    /** Uma notinha com a ponta dobrada, na cor padrao da paleta. */
    public static BufferedImage trayIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int pad = Math.max(1, size / 8);
        int side = size - 2 * pad;
        int fold = Math.max(3, side / 3);

        Palette base = Palette.at(0); // a cor padrao da paleta e a identidade do app
        g.setColor(base.body());
        g.fillRect(pad, pad, side, side);

        // ponta dobrada no canto inferior direito
        g.setColor(base.header().darker());
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
