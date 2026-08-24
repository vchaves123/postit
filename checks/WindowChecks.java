import com.recados.Note;
import com.recados.Palette;
import com.recados.NoteFrame;
import com.recados.NoteStore;
import com.recados.RecadosApp;
import com.recados.Trace;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;

/** Minimizar preserva a nota; encerrar o processo nao minimiza; apagar nao ressuscita. */
public final class WindowChecks {

    public static void run() throws Exception {
        minimizarPreservaANota();
        wmCloseNaoMinimiza();
        apagarNaoRessuscita();
        somenteAAlcaRedimensiona();
        barraDeFormatacao();
        notaEmBranco();
        gravacaoTentaDeNovo();
        barraDeRolagem();
        zoomDeTextoEJanela();
        diarioDeBordo();
    }

    /**
     * A barra de rolagem usa a cor da nota. A do sistema chega cinza, e uma faixa cinza no
     * meio de uma nota colorida se anuncia como "componente" em vez de parte do papel.
     */
    private static void barraDeRolagem() throws Exception {
        Check.grupo("Barra de rolagem com a cor da nota");
        Path base = Files.createTempDirectory("recados-rolagem");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.colorIndex(Palette.indexOf("Rosa"));
        nota.text("texto\nque\npassa\ndo\ntamanho\nda\nnota\ne\nfaz\naparecer\na\nbarra");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);

        Check.that("a barra e pintada por nos, e estreita", frame.scrollBarStyled());
        Check.that("e o fundo dela e a cor da nota",
                frame.scrollBarBackground().equals(Palette.at(Palette.indexOf("Rosa")).body()));

        // trocar a cor da nota tem de levar a barra junto
        SwingUtilities.invokeAndWait(frame::cycleColor);
        Check.that("trocar a cor repinta a barra",
                frame.scrollBarBackground().equals(nota.palette().body()));
        Check.that("e a cor mudou de verdade",
                !nota.palette().name().equals("Rosa"));

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /**
     * Ctrl+ e Ctrl- mudam a fonte <b>e a janela na mesma proporcao</b>, para o texto
     * continuar ocupando o mesmo espaco relativo dentro da nota. O tamanho fica gravado.
     */
    private static void zoomDeTextoEJanela() throws Exception {
        Check.grupo("Zoom de texto e janela");
        Path base = Files.createTempDirectory("recados-fonte");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.text("tanto faz o texto");
        nota.size(280, 260);
        store.save(nota);
        NoteFrame frame = abrir(nota, app);
        Check.that("comeca no tamanho padrao", nota.fontSize() == Note.DEFAULT_FONT_SIZE);

        SwingUtilities.invokeAndWait(frame::zoomIn);
        Check.that("Ctrl+ aumentou a fonte", nota.fontSize() == 12);
        Check.that("e a janela cresceu na mesma proporcao",
                nota.width() == Math.round(280 * 12 / 11.0)
                        && nota.height() == Math.round(260 * 12 / 11.0));
        Check.that("a janela na tela acompanhou a nota",
                frame.getWidth() == nota.width() && frame.getHeight() == nota.height());

        SwingUtilities.invokeAndWait(frame::zoomOut);
        Check.that("Ctrl- voltou a fonte", nota.fontSize() == Note.DEFAULT_FONT_SIZE);
        Check.that("e a janela voltou ao tamanho de antes",
                nota.width() == 280 && nota.height() == 260);

        // o limite existe: fonte nao vira zero nem a janela some
        for (int i = 0; i < 20; i++) {
            SwingUtilities.invokeAndWait(frame::zoomOut);
        }
        Check.that("o menor tamanho de fonte tem piso", nota.fontSize() >= 8);
        Check.that("e a janela nunca fica menor que o minimo",
                nota.width() >= Note.MIN_WIDTH && nota.height() >= Note.MIN_HEIGHT);

        SwingUtilities.invokeAndWait(frame::resetZoom);
        Check.that("Ctrl 0 devolve o tamanho padrao",
                nota.fontSize() == Note.DEFAULT_FONT_SIZE);

        SwingUtilities.invokeAndWait(frame::flush);
        String arquivo = Files.readString(base.resolve("notes").resolve(nota.id() + ".html"));
        Check.that("gravou o tamanho no arquivo",
                arquivo.contains("<meta name=\"recados:font-size\" content=\"11\">"));
        Check.that("o tamanho volta do disco",
                new NoteStore(base).loadAll().get(0).fontSize() == Note.DEFAULT_FONT_SIZE);

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /**
     * Gravacao que falha e tentada de novo sozinha. No Windows a troca do arquivo falha
     * enquanto outro processo estiver com ele aberto por um instante -- antivirus, indexador
     * --, e como o resultado era ignorado, o que o usuario escreveu ia embora em silencio.
     * Aqui o obstaculo e uma pasta ocupada no lugar do arquivo; tirando ela, a nota tem de
     * chegar ao disco sem o usuario fazer nada.
     */
    private static void gravacaoTentaDeNovo() throws Exception {
        Check.grupo("Gravacao que falha e tentada de novo");
        Path base = Files.createTempDirectory("recados-retry");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.html("<html><body>texto que nao pode se perder</body></html>");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);

        Path arquivo = base.resolve("notes").resolve(nota.id() + ".html");
        Path ocupado = arquivo.resolve("ocupado.txt");
        Files.delete(arquivo);
        Files.createDirectory(arquivo);
        Files.writeString(ocupado, "impede a gravacao");

        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("a gravacao nao passou, como esperado", Files.isDirectory(arquivo));

        // o obstaculo sai; ninguem toca na nota
        Files.delete(ocupado);
        Files.delete(arquivo);
        Thread.sleep(1500); // mais que o atraso do autosave
        SwingUtilities.invokeAndWait(() -> { });

        Check.that("a nota chegou ao disco sozinha", Files.isRegularFile(arquivo));
        Check.that("com o texto intacto",
                Files.readString(arquivo).contains("texto que nao pode se perder"));

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /**
     * A barra de formatacao aparece com a nota em foco e nenhum botao dela pode receber o
     * foco -- botao que rouba o foco do editor apaga a selecao na tela, e quem marcou uma
     * palavra antes de clicar no B perde de vista o que marcou.
     */
    private static void barraDeFormatacao() throws Exception {
        Check.grupo("Barra de formatacao");
        Path base = Files.createTempDirectory("recados-barra");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.text("texto para formatar");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);

        Check.that("nasce escondida, sem foco", !frame.formatBarVisible());

        SwingUtilities.invokeAndWait(() ->
                frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_ACTIVATED)));
        Check.that("aparece com a nota em foco", frame.formatBarVisible());

        SwingUtilities.invokeAndWait(() ->
                frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_DEACTIVATED)));
        Check.that("some quando a nota perde o foco", !frame.formatBarVisible());

        Check.that("nenhum botao da barra rouba o foco", !frame.formatBarStealsFocus());
        Check.that("a altura minima da nota cabe a barra", Note.MIN_HEIGHT >= 150);
        // cada botao novo aperta este limite; e aqui que o proximo vai avisar
        Check.that("a barra e a alca cabem na nota mais estreita",
                frame.formatBarFitsIn(Note.MIN_WIDTH));

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /**
     * Nota em branco vai embora direto: sem confirmacao e sem lixeira. Nota com conteudo
     * continua protegida.
     */
    private static void notaEmBranco() throws Exception {
        Check.grupo("Nota em branco apaga direto");
        Path base = Files.createTempDirectory("recados-branco-janela");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note vazia = Note.create();
        store.save(vazia);
        NoteFrame semTexto = abrir(vazia, app);
        Check.that("nota nova e considerada em branco", semTexto.isBlank());

        Note comTexto = Note.create();
        comTexto.text("tem conteudo");
        store.save(comTexto);
        NoteFrame comConteudo = abrir(comTexto, app);
        Check.that("nota com texto nao e em branco", !comConteudo.isBlank());

        // so espaco e quebra de linha continua sendo em branco
        Note soEspacos = Note.create();
        soEspacos.html("<html><body>   <br>  </body></html>");
        store.save(soEspacos);
        NoteFrame emBranco = abrir(soEspacos, app);
        Check.that("espacos e quebras de linha nao sao conteudo", emBranco.isBlank());

        SwingUtilities.invokeAndWait(() -> {
            app.deleteNote(semTexto); // em branco: nao abre dialogo nenhum
        });
        Check.that("apagou sem passar pela lixeira", !store.trashHasNotes());
        Check.that("o arquivo sumiu",
                !Files.exists(base.resolve("notes").resolve(vazia.id() + ".html")));
        Check.that("a janela fechou", !semTexto.isShowing());
        Check.that("a nota com conteudo continua",
                Files.exists(base.resolve("notes").resolve(comTexto.id() + ".html")));

        SwingUtilities.invokeAndWait(() -> {
            comConteudo.dispose();
            emBranco.dispose();
        });
    }

    /**
     * Passar a nota para um monitor de escala diferente fazia o Java reinterpretar o tamanho
     * (280x260 chegava como 350x325 num monitor a 125%), e isso era gravado -- a nota crescia
     * 25% a cada travessia, para sempre. Agora so a alca muda o tamanho gravado.
     */
    private static void somenteAAlcaRedimensiona() throws Exception {
        Check.grupo("Somente a alca redimensiona");
        Path base = Files.createTempDirectory("recados-resize");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.text("tamanho escolhido");
        nota.size(280, 260);
        store.save(nota);
        NoteFrame frame = abrir(nota, app);
        Check.that("abriu no tamanho da nota",
                frame.getWidth() == 280 && frame.getHeight() == 260);

        // e isto que o Windows faz ao trocar de monitor: mexe no tamanho por fora
        SwingUtilities.invokeAndWait(() -> frame.setSize(350, 325));
        SwingUtilities.invokeAndWait(() -> { });
        Thread.sleep(200);
        SwingUtilities.invokeAndWait(() -> { });

        Check.that("a nota nao gravou o tamanho novo",
                nota.width() == 280 && nota.height() == 260);
        Check.that("a janela voltou ao tamanho da nota",
                frame.getWidth() == 280 && frame.getHeight() == 260);
        Check.that("nada disso vazou para o disco",
                new NoteStore(base).loadAll().get(0).width() == 280);

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    private static void minimizarPreservaANota() throws Exception {
        Check.grupo("Minimizar nao apaga");
        Path base = Files.createTempDirectory("recados-min");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.text("texto que nao pode se perder ao minimizar");
        store.save(nota);

        NoteFrame frame = abrir(nota, app);
        Check.that("janela apareceu", frame.isShowing());

        SwingUtilities.invokeAndWait(() -> app.closeNote(frame));

        Check.that("janela saiu da tela", !frame.isShowing());
        Check.that("nota marcada como minimizada", !nota.visible());
        Check.that("o arquivo foi para a pasta de minimizadas", Files.exists(
                base.resolve("notes").resolve("minimizados").resolve(nota.id() + ".html")));
        Check.that("e saiu da pasta das notas na tela",
                !Files.exists(base.resolve("notes").resolve(nota.id() + ".html")));
        Check.that("nada foi para a lixeira", !store.trashHasNotes());

        List<Note> recarregadas = new NoteStore(base).loadAll();
        Check.that("nota continua existindo", recarregadas.size() == 1);
        Check.that("texto intacto", recarregadas.size() == 1
                && recarregadas.get(0).text().startsWith("texto que nao pode se perder"));
        Check.that("volta como minimizada", recarregadas.size() == 1
                && !recarregadas.get(0).visible());
    }

    /**
     * O caso que passou batido e quebrou de verdade: WM_CLOSE chega tambem quando alguem
     * encerra o processo ou o Windows desliga. Se isso minimizar, o proximo inicio abre sem
     * janela nenhuma.
     */
    private static void wmCloseNaoMinimiza() throws Exception {
        Check.grupo("WM_CLOSE nao minimiza");
        Path base = Files.createTempDirectory("recados-wmclose");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.text("aberta quando o processo morreu");
        store.save(nota);

        NoteFrame frame = abrir(nota, app);
        SwingUtilities.invokeAndWait(() ->
                frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
        SwingUtilities.invokeAndWait(() -> { }); // deixa a fila de eventos drenar

        Check.that("nota continua visivel na memoria", nota.visible());
        List<Note> recarregadas = new NoteStore(base).loadAll();
        Check.that("nota continua visivel no disco",
                recarregadas.size() == 1 && recarregadas.get(0).visible());
        Check.that("texto foi gravado ao receber WM_CLOSE", recarregadas.size() == 1
                && recarregadas.get(0).text().startsWith("aberta quando o processo morreu"));
        Check.that("nada foi para a lixeira", !store.trashHasNotes());

        SwingUtilities.invokeAndWait(frame::dispose);
    }

    /**
     * Um autosave pendente disparando depois do apagar regravava o arquivo, e a nota
     * voltava no proximo inicio.
     */
    private static void apagarNaoRessuscita() throws Exception {
        Check.grupo("Apagar nao ressuscita a nota");
        Path base = Files.createTempDirectory("recados-discard");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        Note nota = Note.create();
        nota.text("apagada com save pendente");
        store.save(nota);
        NoteFrame frame = abrir(nota, app);

        Path arquivo = base.resolve("notes").resolve(nota.id() + ".html");
        SwingUtilities.invokeAndWait(() -> {
            frame.scheduleSave();  // deixa um autosave pendente
            store.delete(nota);    // manda para a lixeira
            frame.discard();       // fecha a janela de uma nota que deixou de existir
        });
        Check.that("saiu de notes", !Files.exists(arquivo));
        Check.that("esta na lixeira", store.trashHasNotes());

        Thread.sleep(1200); // mais que o atraso do autosave
        SwingUtilities.invokeAndWait(() -> { });
        Check.that("nao voltou depois do atraso do autosave", !Files.exists(arquivo));
        Check.that("nao aparece ao recarregar", new NoteStore(base).loadAll().isEmpty());
    }


    /**
     * O diario de bordo nasce desligado e nao muda o comportamento de nada. Sem esta
     * checagem, o risco e sutil: um dia alguem liga o trace "so para testar", esquece, e o
     * aplicativo do usuario passa a gravar cada tecla num arquivo -- em cima das notas dele.
     */
    private static void diarioDeBordo() throws Exception {
        Check.grupo("Diario de bordo desligado por padrao");
        Check.that("desligado sem a propriedade", !Trace.ligado());
        Check.that("nao abriu arquivo nenhum", Trace.arquivo() == null);

        boolean[] rodou = {false};
        Trace.comando("teste", "sem estado", () -> rodou[0] = true, () -> "");
        Check.that("desligado, o comando roda assim mesmo", rodou[0]);

        Note nota = Note.create();
        nota.text("com o diario desligado");
        Path base = Files.createTempDirectory("recados-trace");
        RecadosApp app = new RecadosApp(new NoteStore(base));
        NoteFrame frame = abrir(nota, app);
        SwingUtilities.invokeAndWait(() -> frame.type(" mais texto"));
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("digitou", frame.note().text().contains("mais texto"));
        SwingUtilities.invokeAndWait(frame::undo);
        SwingUtilities.invokeAndWait(frame::flush);
        Check.that("desfez", !frame.note().text().contains("mais texto"));
        SwingUtilities.invokeAndWait(frame::discard);
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
