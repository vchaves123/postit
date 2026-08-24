package com.recados;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        return "<html><body>"
                + toParagraphs(escapeHtml(text).replace("\n", "<br>"))
                + "</body></html>";
    }

    private static final Pattern BLOCK = Pattern.compile(
            "(?i)<(p|ul|ol|li|div|table|tr|td|h[1-6]|blockquote)[\\s>/]");

    /** Se o trecho ja tem estrutura de bloco, ou e uma linha corrida separada por {@code <br>}. */
    static boolean hasBlocks(String inlineHtml) {
        return BLOCK.matcher(inlineHtml).find();
    }

    /**
     * Conteudo solto -- texto com {@code <br>} -- virando <b>um parágrafo por linha</b>.
     *
     * <p>Isto nao e cosmetica: e o que faz a tecla ENTER funcionar. O {@code insert-break} do
     * Swing divide o paragrafo onde o cursor esta, e quando o texto nao esta dentro de um
     * {@code <p>} de verdade (o Swing chama isso de {@code p-implied}) nao ha o que dividir --
     * a tecla insere um {@code "\n"} cru, que em HTML e espaco em branco e nao aparece na
     * tela. Nota digitada aqui, nota antiga e nota convertida de RTF chegavam todas assim.
     *
     * <p>Linha em branco continua linha em branco: {@code <p></p>} ocupa a mesma altura que o
     * {@code <br>} que ele substitui (medido: 30px entre duas linhas nos dois casos).
     *
     * <p>Trecho que ja tem bloco -- lista, paragrafo, tabela -- passa intacto.
     */
    static String toParagraphs(String inlineHtml) {
        if (inlineHtml == null || inlineHtml.isBlank() || hasBlocks(inlineHtml)) {
            return inlineHtml == null ? "" : inlineHtml;
        }
        StringBuilder out = new StringBuilder();
        for (String line : inlineHtml.split("(?i)<br\\s*/?>", -1)) {
            out.append("<p>").append(line.strip()).append("</p>");
        }
        return out.toString();
    }

    /**
     * Desmonta a formatacao de um trecho: lista vira linha de texto, negrito/italico/
     * sublinhado somem, e o link <b>fica</b> -- link nao e decoracao, e conteudo: perder o
     * endereco ao limpar formatacao seria perder informacao que nao esta em outro lugar.
     */
    static String flatten(String inlineHtml) {
        String out = inlineHtml.replaceAll("(?i)</(li|p|div|h[1-6])\\s*>", "<br>");
        // O (\s[^>]*)? no fim, e nao [^>]*, e essencial: com [^>]* o padrao de <b> engole o
        // <br> -- "b" e prefixo de "br" -- e as linhas que acabaram de ser criadas somem,
        // juntando a nota toda numa linha. Foi exatamente o que aconteceu.
        out = out.replaceAll(
                "(?i)</?(ul|ol|li|p|div|span|font|b|i|u|em|strong|h[1-6])(\\s[^>]*)?>", "");
        out = out.replaceAll("(?i)^(\\s*<br\\s*/?>)+", "");
        out = out.replaceAll("(?i)(<br\\s*/?>\\s*)+$", "");
        return out.strip();
    }

    static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final Pattern PARAGRAPH_TAG = Pattern.compile("(?i)</?p(\\s[^>]*)?>");

    /**
     * O conteudo de um trecho sem as tags de paragrafo em volta, e com a formatacao de dentro
     * intacta. O escritor de HTML embrulha qualquer trecho num {@code <p>}, mesmo quando o
     * trecho e meia linha; para remontar um paragrafo isso tem de sair.
     */
    static String inline(String html) {
        return PARAGRAPH_TAG.matcher(body(html)).replaceAll("").strip();
    }

    private static final Pattern MONO_TAG = Pattern.compile("(?i)</?tt(\\s[^>]*)?>");

    /** Tira a marca de monoespacado de um trecho, deixando o resto da formatacao. */
    static String withoutMonospace(String html) {
        return MONO_TAG.matcher(html).replaceAll("");
    }

    private static final Pattern FONT_METRICS = Pattern.compile(
            "(?i)\\s(face|size)=\"[^\"]*\"");

    /**
     * Tira {@code face} e {@code size} das tags {@code <font>}. O Swing marca a fonte e o
     * tamanho em cada trecho do texto -- e assim que eles mudam na tela --, mas no arquivo
     * isso e ruim duas vezes: atropela o {@code <style>} que o navegador leria, e a familia
     * que o Swing escreve para a monoespacada e {@code Monospaced}, um nome logico que so o
     * Java conhece (o navegador cairia em qualquer fonte). Fonte e tamanho ficam nos
     * metadados e no {@code <style>}; a janela os reaplica ao abrir.
     */
    static String withoutFontMetrics(String html) {
        return FONT_METRICS.matcher(html).replaceAll("");
    }

    private static final Pattern BODY = Pattern.compile("(?is)<body[^>]*>(.*)</body>");

    /**
     * O conteudo do {@code <body>}. O escritor de HTML do Swing as vezes devolve
     * {@code <html><head>...</head>conteudo</html>}, sem {@code <body>} nenhum -- entao, na
     * falta dele, o que sai daqui e o documento sem o cabecalho, e nao o documento inteiro:
     * gravar um {@code <html>} dentro do {@code <body>} do arquivo daria um HTML torto.
     */
    static String body(String html) {
        Matcher matcher = BODY.matcher(html);
        if (matcher.find()) {
            return matcher.group(1).strip();
        }
        return html.replaceAll("(?is)<!doctype[^>]*>", "")
                .replaceAll("(?is)<head[^>]*>.*?</head>", "")
                .replaceAll("(?is)</?html[^>]*>", "")
                .strip();
    }

    /**
     * Onde uma linha termina e a outra comeca. Num documento do Swing as linhas de uma nota
     * costumam ser {@code <br>} dentro de <b>um</b> paragrafo -- e nao um paragrafo cada --,
     * entao quebrar por linha e quebrar no {@code <br>}. Mas texto <b>colado</b> de fora
     * chega diferente: o Swing faz um {@code <p>} por linha. Os dois casos existem na mesma
     * nota, e por isso os dois contam como fim de linha.
     */
    private static final Pattern LINE_BOUNDARY = Pattern.compile(
            "(?i)<br\\s*/?>|</p\\s*>\\s*<p(\\s[^>]*)?>|</p\\s*>|<p(\\s[^>]*)?>"
                    + "|</?div(\\s[^>]*)?>");

    /**
     * Divide um trecho de HTML em linhas, ja sem as tags de bloco e sem as linhas vazias --
     * linha em branco nao vira item de lista. A formatacao de dentro da linha (negrito,
     * italico, link) vem junto, porque o corte e feito no HTML e nao no texto puro.
     */
    static List<String> lines(String inlineHtml) {
        List<String> lines = new ArrayList<>();
        for (String piece : LINE_BOUNDARY.split(inlineHtml)) {
            String line = piece.strip();
            if (!line.isEmpty() && !stripTags(line).isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static final Pattern TAGS = Pattern.compile("(?s)<[^>]*>");

    private static String stripTags(String html) {
        return TAGS.matcher(html).replaceAll("").replace("&nbsp;", " ");
    }
}
