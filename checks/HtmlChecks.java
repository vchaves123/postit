import com.recados.Note;
import com.recados.NoteFrame;
import com.recados.NoteStore;
import com.recados.RecadosApp;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.rtf.RTFEditorKit;

/** Formatacao em HTML: ida e volta, listas, links, colagem formatada e notas antigas. */
public final class HtmlChecks {

    public static void run() throws Exception {
        idaEVolta();
        listasELinks();
        listaDaSelecao();
        recuoDaLista();
        desfazer();
        teclaEnter();
        limparFormatacao();
        fonteMonoespacada();
        colagemFormatada();
        notasAntigas();
    }

    /**
     * A tecla ENTER. O que ela fazia antes: inseria um {@code "\n"} cru, que em HTML e espaco
     * em branco -- ou seja, <b>nada aparecia</b>. A causa nao estava na tecla: o
     * {@code insert-break} do Swing divide o paragrafo onde o cursor esta, e o texto da nota
     * morava fora de qualquer {@code <p>} (o Swing chama isso de {@code p-implied}). A
     * correcao foi o conteudo passar a morar em paragrafos de verdade.
     */
    private static void teclaEnter() throws Exception {
        Check.grupo("A tecla ENTER");
        Path base = Files.createTempDirectory("recados-enter");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        // nota que chega com <br>, como toda nota digitada e toda nota de versao anterior
        Note nota = Note.create();
        nota.html("<html><body>primeira linha<br>segunda linha</body></html>");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("o conteudo solto virou paragrafo", nota.html().toLowerCase().contains("<p"));
        Check.that("e as linhas continuam sendo duas",
                nota.text().contains("primeira linha") && nota.text().contains("segunda linha"));

        int antes = contagem(nota.html().toLowerCase(), "<p");
        SwingUtilities.invokeAndWait(() -> {
            frame.select(5, 5); // cursor no meio da primeira linha
            frame.pressEnter();
        });
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("ENTER criou um paragrafo novo",
                contagem(nota.html().toLowerCase(), "<p") == antes + 1);

        // O escritor do Swing as vezes devolve <html><head>..</head>conteudo</html>, sem
        // <body> nenhum; se isso for tratado como "o corpo", o arquivo fica com um <html>
        // dentro do <body> -- HTML torto, e o navegador mostra o que der.
        String arquivo = Files.readString(
                base.resolve("notes").resolve(nota.id() + ".html")).toLowerCase();
        Check.that("o arquivo tem um <html> so", contagem(arquivo, "<html") == 1);
        Check.that("e o body nao tem documento dentro",
                !arquivo.substring(arquivo.indexOf("<body")).contains("<html"));
        Check.that("e nao tem cabecalho dentro do body",
                !arquivo.substring(arquivo.indexOf("<body")).contains("<head"));

        SwingUtilities.invokeAndWait(frame::dispose);

        // dentro de lista, ENTER cria o item seguinte
        Note comLista = Note.create();
        comLista.html("<html><body><ul><li>um</li><li>dois</li></ul></body></html>");
        store.save(comLista);
        NoteFrame naLista = abrir(comLista, app);
        SwingUtilities.invokeAndWait(() -> {
            naLista.select(3, 3); // fim do primeiro item
            naLista.pressEnter();
        });
        SwingUtilities.invokeAndWait(naLista::flush);
        Check.that("ENTER na lista criou um item, nao um paragrafo",
                contagem(comLista.html().toLowerCase(), "<li") == 3);
        Check.that("a lista continua sendo uma so",
                contagem(comLista.html().toLowerCase(), "<ul") == 1);
        Check.that("e o cursor ficou dentro da lista", naLista.caretInsideList());

        // ENTER no item vazio sai da lista -- sem isso nao ha como terminar uma lista
        // sem usar o mouse
        SwingUtilities.invokeAndWait(naLista::pressEnter);
        SwingUtilities.invokeAndWait(naLista::flush);
        Check.that("ENTER no item vazio tirou o item",
                contagem(comLista.html().toLowerCase(), "<li") == 2);
        Check.that("e o cursor saiu da lista", !naLista.caretInsideList());

        SwingUtilities.invokeAndWait(naLista::dispose);
    }

    /**
     * Limpar formatacao desmonta a lista: item volta a ser linha de texto. O link fica --
     * link nao e decoracao, e conteudo, e perder o endereco seria perder informacao que nao
     * esta em outro lugar.
     */
    private static void limparFormatacao() throws Exception {
        Check.grupo("Limpar formatacao desmonta a lista");
        Path base = Files.createTempDirectory("recados-limpar");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.html("<html><body><ul><li><b>um</b></li><li>dois</li></ul>"
                + "<p>fora da lista</p></body></html>");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);

        SwingUtilities.invokeAndWait(() -> {
            frame.select(0, 0); // sem selecao: vale a nota toda
            frame.clearFormatting();
        });
        SwingUtilities.invokeAndWait(frame::flush);
        String html = semCor(nota.html().toLowerCase());
        Check.that("a lista virou texto", !html.contains("<ul") && !html.contains("<li"));
        Check.that("o negrito saiu", !html.contains("<b>"));
        Check.that("o texto dos itens ficou", nota.text().contains("um")
                && nota.text().contains("dois") && nota.text().contains("fora da lista"));
        Check.that("cada item virou uma linha",
                contagem(html, "<p") >= 3);

        SwingUtilities.invokeAndWait(frame::dispose);

        // Com selecao dentro da lista, a lista inteira e desmontada -- e o que estava fora
        // dela fica intacto. A primeira versao removia o texto selecionado e enfiava as
        // linhas DENTRO do <li> que sobrava: o marcador do primeiro item continuava na tela e
        // as linhas saiam indentadas.
        Note comSelecao = Note.create();
        comSelecao.html("<html><body><p>antes</p><ol><li>um</li><li>dois</li><li>tres</li></ol>"
                + "<p><b>depois</b></p></body></html>");
        store.save(comSelecao);
        NoteFrame naSelecao = abrir(comSelecao, app);
        SwingUtilities.invokeAndWait(() -> {
            naSelecao.select(8, 18); // do meio do primeiro item ate o ultimo
            naSelecao.clearFormatting();
        });
        SwingUtilities.invokeAndWait(naSelecao::flush);
        String comSel = semCor(comSelecao.html().toLowerCase());
        Check.that("selecao parcial desmontou a lista inteira",
                !comSel.contains("<ol") && !comSel.contains("<li"));
        Check.that("nenhum paragrafo ficou dentro de item", !comSel.contains("<li><p"));
        Check.that("os itens viraram linhas soltas", contagem(comSel, "<p") == 5);
        Check.that("o texto de fora da lista ficou intacto",
                comSelecao.text().contains("antes") && comSelecao.text().contains("depois"));
        Check.that("e o negrito de fora da selecao continua",
                comSel.contains("<b>depois</b>"));
        SwingUtilities.invokeAndWait(naSelecao::dispose);

        // o link e conteudo, nao formatacao: sobrevive
        Note comLink = Note.create();
        comLink.html("<html><body><ul><li><a href=\"https://exemplo.org\">site</a></li>"
                + "</ul></body></html>");
        store.save(comLink);
        NoteFrame outra = abrir(comLink, app);
        SwingUtilities.invokeAndWait(() -> {
            outra.select(0, 0);
            outra.clearFormatting();
        });
        SwingUtilities.invokeAndWait(outra::flush);
        Check.that("a lista com link tambem virou texto",
                !comLink.html().toLowerCase().contains("<li"));
        Check.that("mas o link continua la", comLink.html().contains("https://exemplo.org"));
        SwingUtilities.invokeAndWait(outra::dispose);
    }

    /**
     * Desfazer e refazer. As duas armadilhas que isto precisa evitar:
     *
     * <p>A carga da nota nao pode estar na pilha -- para o documento, abrir a nota e inserir
     * texto, e um Ctrl+Z logo depois de abrir apagaria a nota inteira.
     *
     * <p>Uma acao que mexe no documento varias vezes tem de voltar num passo. Meia conversao
     * desfeita deixaria a nota num estado que nunca existiu na tela.
     */
    private static void desfazer() throws Exception {
        Check.grupo("Desfazer");
        Path base = Files.createTempDirectory("recados-undo");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.html("<html><body>um<br>dois<br>tres</body></html>");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);

        Check.that("nota recem-aberta nao tem nada para desfazer", !frame.canUndo());
        SwingUtilities.invokeAndWait(frame::undo);
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("Ctrl+Z numa nota recem-aberta nao apaga o texto",
                nota.text().contains("um") && nota.text().contains("tres"));

        SwingUtilities.invokeAndWait(() -> {
            frame.select(1, 500);
            frame.insertList();
        });
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("a lista foi feita", nota.html().toLowerCase().contains("<ul"));
        Check.that("e ha o que desfazer", frame.canUndo());

        SwingUtilities.invokeAndWait(frame::undo);
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("desfazer tirou a lista", !nota.html().toLowerCase().contains("<ul"));
        Check.that("e devolveu o texto", nota.text().contains("um")
                && nota.text().contains("dois") && nota.text().contains("tres"));
        Check.that("a conversao voltou num passo so, nao sobrou meia", !frame.canUndo());
        Check.that("e da para refazer", frame.canRedo());

        SwingUtilities.invokeAndWait(frame::redo);
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("refazer trouxe a lista de volta", nota.html().toLowerCase().contains("<ul"));
        Check.that("com os tres itens", contagem(nota.html().toLowerCase(), "<li") == 3);

        // o link tambem apaga a selecao e insere: um passo, nao dois
        SwingUtilities.invokeAndWait(() -> {
            frame.select(1, 3);
            frame.insertLink("https://exemplo.org", "site");
        });
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("o link entrou", nota.html().contains("exemplo.org"));
        SwingUtilities.invokeAndWait(frame::undo);
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("desfazer tirou o link inteiro", !nota.html().contains("exemplo.org"));
        Check.that("e devolveu o texto que estava selecionado", nota.text().contains("um"));

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /**
     * Com texto selecionado, o botao de lista transforma <b>cada linha selecionada</b> num
     * item. Antes ele inseria um item vazio no meio do texto marcado, ignorando a selecao.
     *
     * <p>Linha aqui e {@code <br>}: no documento do Swing as linhas de uma nota ficam todas
     * num paragrafo so, separadas por {@code <br>} -- nao um paragrafo cada.
     */
    private static void listaDaSelecao() throws Exception {
        Check.grupo("HTML: lista a partir da selecao");
        Path base = Files.createTempDirectory("recados-lista");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        String html = comLista(app, store, "um<br>dois<br>tres", 1, 500);
        Check.that("tres linhas viraram tres itens", contagem(html, "<li") == 3);
        Check.that("cada item ficou com o seu texto", html.contains("um") && html.contains("dois")
                && html.contains("tres"));
        Check.that("nao sobrou quebra solta fazendo linha vazia", !html.contains("<br"));

        // a selecao e esticada para as bordas: meia palavra na ponta leva a linha inteira
        String parcial = comLista(app, store, "primeira<br>segunda<br>terceira", 5, 15);
        Check.that("selecao parcial virou duas linhas inteiras", contagem(parcial, "<li") == 2);
        Check.that("a primeira linha entrou inteira", parcial.contains("primeira"));
        Check.that("a linha nao selecionada ficou fora da lista",
                parcial.indexOf("terceira") > parcial.indexOf("</ul>"));

        // sem as tags de cor, que o escritor do Swing intercala e que vem da paleta
        String negrito = semCor(comLista(app, store, "um<br><b>dois</b><br>tres", 1, 500));
        Check.that("o negrito de dentro da linha sobreviveu", negrito.contains("<b>dois</b>"));

        String link = comLista(app, store,
                "um<br><a href=\"https://exemplo.org\">site</a><br>tres", 1, 500);
        Check.that("o link de dentro da linha sobreviveu",
                link.contains("href=\"https://exemplo.org\"") && link.contains("site"));

        String vazia = comLista(app, store, "um<br><br>dois", 1, 500);
        Check.that("linha em branco nao vira item", contagem(vazia, "<li") == 2);

        // selecao comecando em zero: o HTMLDocument tem uma quebra propria no offset 0, e
        // parar nela deixava a lista vazia
        String tudo = comLista(app, store, "um<br>dois", 0, 500);
        Check.that("Ctrl+A tambem funciona", contagem(tudo, "<li") == 2);

        // Texto colado de fora (do Notepad, do navegador) nao chega com <br>: o Swing faz um
        // <p> por linha. Convertendo, o paragrafo esvaziado nao pode ficar para tras -- ele
        // virava uma linha em branco antes da lista.
        String colado = comLista(app, store, "<p>um</p><p>dois</p><p>tres</p>", 0, 500);
        Check.that("texto colado em paragrafos vira tres itens", contagem(colado, "<li") == 3);
        Check.that("nao sobrou paragrafo vazio antes da lista", !colado.contains("<p"));

        String semSelecao = comLista(app, store, "um<br>dois", 3, 3);
        Check.that("sem selecao, insere um item vazio para digitar",
                contagem(semSelecao, "<li") == 1);
        Check.that("sem selecao, o texto continua la",
                semSelecao.contains("um") && semSelecao.contains("dois"));

        // a numerada e a mesma logica com <ol> no lugar de <ul>
        String numerada = comLista(app, store, "um<br>dois<br>tres", 1, 500, true);
        Check.that("a numerada usa <ol>", numerada.contains("<ol"));
        Check.that("e nao <ul>", !numerada.contains("<ul"));
        Check.that("com um item por linha selecionada", contagem(numerada, "<li") == 3);

        String numeradaColada = comLista(app, store, "<p>um</p><p>dois</p>", 0, 500, true);
        Check.that("numerada tambem no texto colado em paragrafos",
                numeradaColada.contains("<ol") && contagem(numeradaColada, "<li") == 2);

        String numeradaVazia = comLista(app, store, "um<br>dois", 3, 3, true);
        Check.that("numerada sem selecao insere um item vazio",
                numeradaVazia.contains("<ol") && contagem(numeradaVazia, "<li") == 1);
    }

    /**
     * O recuo da lista e o escolhido, e nao o padrao do Swing -- que e 50px e, numa nota de
     * 280px, come um sexto da linha e joga o marcador para o meio do nada. A medida e feita
     * no pixel pintado, e nao no CSS, porque e o pixel que o usuario ve.
     */
    private static void recuoDaLista() throws Exception {
        Check.grupo("HTML: o recuo da lista");
        int[] medidas = new int[4]; // texto normal: x e pixel; item: x e pixel
        SwingUtilities.invokeAndWait(() -> {
            JTextPane pane = new JTextPane();
            pane.setEditorKit(NoteFrame.htmlKit()); // o CSS de verdade da nota
            pane.setBackground(Color.WHITE);
            pane.setForeground(Color.BLACK);
            pane.setText("<html><body>normal<ul><li>item</li></ul></body></html>");
            pane.setSize(280, 160);
            pane.doLayout();

            BufferedImage img = new BufferedImage(280, 160, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 280, 160);
            pane.paint(g);
            g.dispose();
            try {
                String texto = pane.getDocument().getText(0, pane.getDocument().getLength());
                Rectangle2D normal = pane.modelToView2D(texto.indexOf("normal"));
                Rectangle2D item = pane.modelToView2D(texto.indexOf("item"));
                medidas[0] = (int) normal.getX();
                medidas[1] = primeiroPixel(img, (int) normal.getY(), (int) normal.getMaxY());
                medidas[2] = (int) item.getX();
                medidas[3] = primeiroPixel(img, (int) item.getY(), (int) item.getMaxY());
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        });
        int marcador = medidas[3] - medidas[1];
        int texto = medidas[2] - medidas[0];
        Check.that("o marcador recua os 10px escolhidos, nao os 50 do padrao (" + marcador + ")",
                marcador >= 1 && marcador <= 9);
        Check.that("o texto do item acompanha o marcador (" + texto + ")",
                texto >= 9 && texto <= 17);
        Check.that("e sobra linha de texto util numa nota estreita",
                Note.MIN_WIDTH - texto > 120);
    }

    /** A coluna do pixel pintado mais a esquerda naquela faixa de linhas. */
    private static int primeiroPixel(BufferedImage img, int topo, int base) {
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = Math.max(0, topo); y < Math.min(img.getHeight(), base + 1); y++) {
                if ((img.getRGB(x, y) & 0xFFFFFF) != 0xFFFFFF) {
                    return x;
                }
            }
        }
        return -1;
    }

    private static String comLista(RecadosApp app, NoteStore store, String corpo, int de, int ate)
            throws Exception {
        return comLista(app, store, corpo, de, ate, false);
    }

    /**
     * Fonte monoespacada <b>do trecho selecionado</b>, marcada com {@code <tt>}.
     *
     * <p>Duas coisas que a implementacao teve de descobrir, e que estas checagens travam:
     * marcar o trecho por atributo de fonte desenha certo na tela mas o escritor grava
     * {@code face=""} -- a fonte se perde no disco; e a insercao de HTML do Swing trata
     * {@code <tt>} como bloco e parte o paragrafo em tres, entao o paragrafo tem de ser
     * remontado inteiro.
     */
    private static void fonteMonoespacada() throws Exception {
        Check.grupo("Fonte monoespacada no trecho");
        Path base = Files.createTempDirectory("recados-mono");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.html("<html><body><p>antes MEIO depois</p></body></html>");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);

        SwingUtilities.invokeAndWait(() -> {
            frame.select(7, 11); // so a palavra MEIO
            frame.toggleMonospaced();
        });
        SwingUtilities.invokeAndWait(frame::flush);
        String html = semCor(nota.html().toLowerCase());
        Check.that("o trecho ficou monoespacado", html.contains("<tt>meio</tt>"));
        Check.that("o resto da linha nao", html.contains("antes <tt>") && html.contains("</tt> depois"));
        Check.that("e o paragrafo continua um so", contagem(html, "<p") == 1);
        Check.that("o menu sabe que o cursor esta num trecho monoespacado",
                frame.monospacedAtCaret());

        String arquivo = Files.readString(base.resolve("notes").resolve(nota.id() + ".html"));
        Check.that("o arquivo guarda a marca", arquivo.contains("<tt>"));
        Check.that("e o style diz qual fonte, para o navegador mostrar igual",
                arquivo.contains("tt { font-family: Consolas"));

        // desligar tira a marca -- e so dela
        SwingUtilities.invokeAndWait(() -> {
            frame.select(7, 11);
            frame.toggleMonospaced();
        });
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("desligar tirou a marca", !nota.html().toLowerCase().contains("<tt"));
        Check.that("e o texto ficou inteiro", nota.text().equals("antes MEIO depois"));
        SwingUtilities.invokeAndWait(frame::dispose);

        // selecao atravessando paragrafos: cada um recebe a sua marca, porque <tt> e inline
        Note varias = Note.create();
        varias.html("<html><body><p>um</p><p>dois</p><p>tres</p></body></html>");
        store.save(varias);
        NoteFrame outra = abrir(varias, app);
        SwingUtilities.invokeAndWait(() -> {
            outra.select(1, 9);
            outra.toggleMonospaced();
        });
        SwingUtilities.invokeAndWait(outra::flush);
        String duas = semCor(varias.html().toLowerCase());
        Check.that("cada paragrafo tocado recebeu a marca", contagem(duas, "<tt>") == 2);
        Check.that("o paragrafo de fora ficou sem ela",
                duas.indexOf("tres") > duas.lastIndexOf("</tt>"));
        Check.that("e nenhum paragrafo foi partido", contagem(duas, "<p") == 3);
        SwingUtilities.invokeAndWait(outra::dispose);

        // sem selecao, vale a nota toda
        Note toda = Note.create();
        toda.html("<html><body><p>uma</p><p>outra</p></body></html>");
        store.save(toda);
        NoteFrame naToda = abrir(toda, app);
        SwingUtilities.invokeAndWait(() -> {
            naToda.select(1, 1);
            naToda.toggleMonospaced();
        });
        SwingUtilities.invokeAndWait(naToda::flush);
        Check.that("sem selecao a nota toda fica monoespacada",
                contagem(semCor(toda.html().toLowerCase()), "<tt>") == 2);
        SwingUtilities.invokeAndWait(naToda::dispose);

        // nota da versao em que a fonte era da nota inteira, num metadado
        Files.writeString(base.resolve("notes").resolve("antiga-mono.html"),
                "<!DOCTYPE html>\n<html><head>"
                        + "<meta name=\"recados:font\" content=\"mono\">"
                        + "</head><body><p>era da nota toda</p></body></html>");
        Note convertida = new NoteStore(base).loadAll().stream()
                .filter(n -> n.id().equals("antiga-mono")).findFirst().orElseThrow();
        Check.that("o metadado antigo virou marca de trecho",
                convertida.html().toLowerCase().contains("<tt>"));
        Check.that("com o texto intacto", convertida.text().contains("era da nota toda"));
    }

    /** Abre uma nota com este corpo, seleciona o trecho, aciona a lista e devolve o HTML. */
    private static String comLista(RecadosApp app, NoteStore store, String corpo, int de, int ate,
            boolean numerada) throws Exception {
        Note nota = Note.create();
        nota.html("<html><body>" + corpo + "</body></html>");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);
        SwingUtilities.invokeAndWait(() -> {
            frame.select(de, ate);
            if (numerada) {
                frame.insertNumberedList();
            } else {
                frame.insertList();
            }
        });
        SwingUtilities.invokeAndWait(frame::flush);
        SwingUtilities.invokeAndWait(frame::dispose);
        return nota.html().toLowerCase();
    }

    /** Tira as tags de cor do HTML: a cor vem da paleta, e nao e o que a checagem observa. */
    private static String semCor(String html) {
        return html.replaceAll("(?i)</?font[^>]*>", "");
    }

    private static int contagem(String texto, String agulha) {
        int total = 0;
        for (int at = texto.indexOf(agulha); at >= 0; at = texto.indexOf(agulha, at + 1)) {
            total++;
        }
        return total;
    }

    private static void idaEVolta() throws Exception {
        Check.grupo("HTML: ida e volta");
        Path base = Files.createTempDirectory("recados-html");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.html("<html><body><b>negrito</b> e <i>italico</i> e <u>sublinhado</u></body></html>");
        nota.text("negrito e italico e sublinhado");
        store.save(nota);

        Note doDisco = new NoteStore(base).loadAll().get(0);
        Check.that("HTML sobreviveu ao disco", doDisco.html().contains("<b>"));

        NoteFrame frame = abrir(doDisco, app);
        SwingUtilities.invokeAndWait(frame::flush);

        String html = doDisco.html().toLowerCase();
        Check.that("negrito sobreviveu ao ciclo abrir/gravar", html.contains("<b>"));
        Check.that("italico sobreviveu", html.contains("<i>"));
        Check.that("sublinhado sobreviveu", html.contains("<u>"));
        Check.that("texto puro tambem foi gravado",
                doDisco.text().contains("negrito e italico e sublinhado"));
        Check.that("titulo do menu nao mostra marcacao", !doDisco.title().contains("<"));
        SwingUtilities.invokeAndWait(frame::dispose);
    }

    private static void listasELinks() throws Exception {
        Check.grupo("HTML: listas e links");
        Path base = Files.createTempDirectory("recados-html2");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.text("compras");
        nota.html("<html><body>compras</body></html>");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);

        SwingUtilities.invokeAndWait(frame::insertList);
        SwingUtilities.invokeAndWait(frame::flush);
        String comLista = nota.html().toLowerCase();
        Check.that("lista virou <ul>", comLista.contains("<ul"));
        Check.that("lista tem item <li>", comLista.contains("<li"));

        SwingUtilities.invokeAndWait(() -> frame.insertLink("https://exemplo.org/pagina", "o site"));
        SwingUtilities.invokeAndWait(frame::flush);
        String comLink = nota.html();
        Check.that("link virou <a href>", comLink.toLowerCase().contains("<a href="));
        Check.that("endereco do link foi gravado", comLink.contains("https://exemplo.org/pagina"));
        Check.that("rotulo do link foi gravado", comLink.contains("o site"));
        Check.that("texto puro tem o rotulo, nao a marcacao",
                nota.text().contains("o site") && !nota.text().contains("<a"));

        // link sem rotulo usa o proprio endereco
        SwingUtilities.invokeAndWait(() -> frame.insertLink("https://exemplo.org/outra", null));
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("link sem rotulo usa o endereco como texto",
                nota.text().contains("https://exemplo.org/outra"));

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /** O motivo de ter trocado RTF por HTML: colar em e-mail e navegador mantendo formato. */
    private static void colagemFormatada() throws Exception {
        Check.grupo("HTML: colagem formatada");
        Path base = Files.createTempDirectory("recados-html3");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.html("<html><body><b>importante</b></body></html>");
        nota.text("importante");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);

        // area de transferencia propria: nao mexe na do usuario
        Clipboard clipboard = new Clipboard("checagem");
        SwingUtilities.invokeAndWait(() -> frame.copyAllTo(clipboard));

        Transferable conteudo = clipboard.getContents(null);
        Check.that("algo foi copiado", conteudo != null);
        boolean temHtml = false;
        boolean temTexto = false;
        String html = "";
        if (conteudo != null) {
            for (DataFlavor flavor : conteudo.getTransferDataFlavors()) {
                if ("text".equals(flavor.getPrimaryType()) && "html".equals(flavor.getSubType())
                        && flavor.getRepresentationClass() == String.class) {
                    temHtml = true;
                    html = conteudo.getTransferData(flavor).toString().toLowerCase();
                }
                if (flavor.equals(DataFlavor.stringFlavor)) {
                    temTexto = true;
                }
            }
        }
        Check.that("oferece text/html para o e-mail e o navegador", temHtml);
        Check.that("oferece texto puro para quem nao aceita HTML", temTexto);
        Check.that("o HTML copiado leva a formatacao", html.contains("<b>"));

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    private static void notasAntigas() throws Exception {
        Check.grupo("HTML: notas de versoes anteriores");
        Path base = Files.createTempDirectory("recados-html4");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        // nota da versao com RTF
        Note comRtf = Note.create();
        comRtf.text("negrito e normal");
        comRtf.rtf(rtfComNegrito());
        store.save(comRtf);
        Check.that("comeca sem HTML", comRtf.html().isEmpty());

        NoteFrame frame = abrir(comRtf, app);
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("RTF antigo virou HTML", comRtf.html().toLowerCase().contains("<b>"));
        Check.that("texto preservado na conversao", comRtf.text().contains("negrito e normal"));

        // nota so com texto puro, anterior a qualquer formatacao
        Note soTexto = Note.create();
        soTexto.text("linha um\nlinha dois & <coisas>");
        store.save(soTexto);
        NoteFrame outra = abrir(soTexto, app);
        SwingUtilities.invokeAndWait(outra::flush);
        Check.that("nota so com texto ganhou HTML", !soTexto.html().isBlank());
        Check.that("quebra de linha preservada", soTexto.text().contains("linha um")
                && soTexto.text().contains("linha dois"));
        Check.that("caracteres de marcacao nao viraram tags",
                soTexto.text().contains("& <coisas>"));

        List<Note> recarregadas = new NoteStore(base).loadAll();
        Check.that("as duas continuam no disco", recarregadas.size() == 2);

        SwingUtilities.invokeAndWait(() -> {
            frame.dispose();
            outra.dispose();
        });
    }

    private static String rtfComNegrito() throws Exception {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        doc.insertString(0, "negrito", bold);
        doc.insertString(doc.getLength(), " e normal", new SimpleAttributeSet());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new RTFEditorKit().write(out, doc, 0, doc.getLength());
        return out.toString(StandardCharsets.ISO_8859_1);
    }

    private static NoteFrame abrir(Note nota, RecadosApp app) throws Exception {
        NoteFrame[] frame = new NoteFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new NoteFrame(nota, app);
            frame[0].setVisible(true);
        });
        return frame[0];
    }
}
