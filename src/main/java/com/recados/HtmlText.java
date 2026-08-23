package com.recados;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.rtf.RTFEditorKit;

/**
 * Texto virando HTML: escapar, converter texto puro e converter o RTF das notas gravadas
 * antes desta versao. Fica em classe propria porque tem dois chamadores -- a janela, quando
 * abre uma nota que ainda estava em RTF, e o {@link NoteStore}, quando converte o arquivo
 * {@code .properties} para {@code .html} -- e a conversao tem de dar o mesmo resultado nos
 * dois caminhos.
 */
final class HtmlText {

    /** Um por classe: o kit nao guarda estado do documento. */
    private static final RTFEditorKit RTF = new RTFEditorKit();

    /**
     * O RTFEditorKit trabalha em bytes -- passar Reader ou Writer para ele levanta
     * "RTF is an 8-bit format". Latin-1 mapeia byte a caractere sem perder nada, entao o
     * RTF cabe numa String e volta identico.
     */
    private static final Charset RTF_CHARSET = StandardCharsets.ISO_8859_1;

    private HtmlText() {
    }

    /** O RTF de uma nota antiga em HTML, ou vazio se nao der para converter. */
    static String rtfToHtml(String rtf) {
        if (rtf == null || rtf.isBlank()) {
            return "";
        }
        try {
            DefaultStyledDocument styled = new DefaultStyledDocument();
            RTF.read(new ByteArrayInputStream(rtf.getBytes(RTF_CHARSET)), styled, 0);
            return styledToHtml(styled);
        } catch (IOException | BadLocationException e) {
            System.err.println("Nao foi possivel converter o RTF de uma nota ("
                    + e.getMessage() + "); fica o texto puro.");
            return "";
        }
    }

    /**
     * Converte um documento formatado em HTML. Feito a mao porque o MinimalHTMLWriter do
     * Swing nao e publico -- e porque as notas em RTF so podiam ter negrito, italico e
     * sublinhado, que e exatamente o que este metodo cobre.
     */
    private static String styledToHtml(StyledDocument doc) throws BadLocationException {
        StringBuilder out = new StringBuilder("<html><body>");
        int position = 0;
        while (position < doc.getLength()) {
            Element run = doc.getCharacterElement(position);
            int start = Math.max(run.getStartOffset(), position);
            int end = run.getEndOffset();
            AttributeSet attrs = run.getAttributes();

            StringBuilder open = new StringBuilder();
            StringBuilder close = new StringBuilder();
            if (StyleConstants.isBold(attrs)) {
                open.append("<b>");
                close.insert(0, "</b>");
            }
            if (StyleConstants.isItalic(attrs)) {
                open.append("<i>");
                close.insert(0, "</i>");
            }
            if (StyleConstants.isUnderline(attrs)) {
                open.append("<u>");
                close.insert(0, "</u>");
            }
            out.append(open)
                    .append(escapeHtml(doc.getText(start, end - start)).replace("\n", "<br>"))
                    .append(close);
            position = Math.max(end, position + 1);
        }
        return out.append("</body></html>").toString();
    }

    /** Texto puro em HTML, escapando o que o navegador leria como marcacao. */
    static String plainToHtml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return "<html><body>" + escapeHtml(text).replace("\n", "<br>") + "</body></html>";
    }

    static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
