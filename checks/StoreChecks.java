import com.recados.Note;
import com.recados.NoteStore;
import com.recados.Palette;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** O arquivo da nota: HTML de verdade, a pasta de minimizadas, lixeira e formatos antigos. */
public final class StoreChecks {

    public static void run() throws Exception {
        arquivoHtml();
        minimizadaEAPasta();
        formatoAntigo();
        gravacaoQueFalha();
        lixeira();
        emBranco();
        migracao();
        cores();
    }

    /**
     * O arquivo tem de ser um HTML que abre no navegador, com os metadados em
     * {@code <meta name="recados:...">} -- e nao em comentario, que editor, minificador ou
     * sanitizador tira sem avisar.
     */
    private static void arquivoHtml() throws Exception {
        Check.grupo("Arquivo HTML de verdade");
        Path base = Files.createTempDirectory("recados-html-file");
        NoteStore store = new NoteStore(base);

        Note nota = Note.create();
        nota.html("<html><body><b>comprar pao</b><br>e leite</body></html>");
        nota.text("comprar pao\ne leite");
        nota.location(120, 340);
        nota.size(300, 280);
        nota.colorIndex(Palette.indexOf("Verde"));
        nota.alwaysOnTop(false);
        store.save(nota);

        Path arquivo = base.resolve("notes").resolve(nota.id() + ".html");
        Check.that("o arquivo e .html", Files.exists(arquivo));
        String conteudo = Files.readString(arquivo);

        Check.that("comeca com doctype", conteudo.startsWith("<!DOCTYPE html>"));
        Check.that("declara utf-8", conteudo.contains("<meta charset=\"utf-8\">"));
        Check.that("tem titulo para a aba do navegador",
                conteudo.contains("<title>comprar pao</title>"));
        Check.that("a formatacao esta no body", conteudo.contains("<b>comprar pao</b>"));
        Check.that("leva a cor da paleta no style", conteudo.contains("background: #C9F5B9"));
        Check.that("avisa para nao renomear", conteudo.contains("Nao renomeie este arquivo"));

        Check.that("posicao em meta", conteudo.contains("<meta name=\"recados:x\" content=\"120\">")
                && conteudo.contains("<meta name=\"recados:y\" content=\"340\">"));
        Check.that("tamanho em meta",
                conteudo.contains("<meta name=\"recados:width\" content=\"300\">")
                        && conteudo.contains("<meta name=\"recados:height\" content=\"280\">"));
        // pelo nome, e nao pela posicao: reordenar a paleta nao pode repintar nota gravada
        Check.that("cor em meta, pelo nome",
                conteudo.contains("<meta name=\"recados:color\" content=\"Verde\">"));
        Check.that("fixar no topo em meta",
                conteudo.contains("<meta name=\"recados:always-on-top\" content=\"false\">"));
        Check.that("a data de criacao e legivel",
                conteudo.matches("(?s).*recados:created-at\" content=\"\\d{4}-\\d\\d-\\d\\dT.*"));
        Check.that("visibilidade NAO esta no arquivo -- ela e a pasta",
                !conteudo.contains("recados:visible"));

        Note doDisco = new NoteStore(base).loadAll().get(0);
        Check.that("id continua vindo do nome do arquivo", doDisco.id().equals(nota.id()));
        Check.that("posicao volta igual", doDisco.x() == 120 && doDisco.y() == 340);
        Check.that("tamanho volta igual", doDisco.width() == 300 && doDisco.height() == 280);
        Check.that("cor volta igual", doDisco.palette().name().equals("Verde"));
        Check.that("fixar no topo volta igual", !doDisco.alwaysOnTop());
        Check.that("data de criacao volta ao milissegundo",
                doDisco.createdAt() == nota.createdAt());
        Check.that("formatacao volta igual", doDisco.html().contains("<b>comprar pao</b>"));
        Check.that("texto puro e derivado do HTML, sem tags",
                doDisco.text().equals("comprar pao\ne leite"));
        Check.that("titulo do menu nao mostra marcacao", !doDisco.title().contains("<"));

        // O arquivo temporario da gravacao atomica nao e nota. Isto ja apareceu de verdade:
        // a gravacao passa por <id>.html.tmp, e quem estiver lendo a pasta nesse instante ve
        // o .tmp -- que nao pode virar uma segunda nota, nem substituir a de verdade.
        Files.writeString(base.resolve("notes").resolve("meia-gravacao.html.tmp"),
                "<html><body>pela metade</body></html>");
        Check.that("arquivo temporario e ignorado",
                new NoteStore(base).loadAll().size() == 1);

        // aspas e sinais de marcacao no texto nao podem quebrar o arquivo
        Note complicada = Note.create();
        complicada.html("<html><body>a &lt; b &amp; \"cotado\"</body></html>");
        store.save(complicada);
        Note voltou = new NoteStore(base).loadAll().stream()
                .filter(n -> n.id().equals(complicada.id())).findFirst().orElseThrow();
        Check.that("marcacao escapada sobrevive ao round-trip",
                voltou.text().equals("a < b & \"cotado\""));
    }

    /**
     * Minimizada nao e um campo no arquivo: e a pasta. O nome do arquivo nunca muda -- com
     * sufixo (algo como .html.minimizado) o Windows perderia a associacao e o duplo clique
     * deixaria de abrir no navegador.
     */
    private static void minimizadaEAPasta() throws Exception {
        Check.grupo("Minimizada e a pasta, nao um campo");
        Path base = Files.createTempDirectory("recados-minimizadas");
        NoteStore store = new NoteStore(base);
        Path emNotes = base.resolve("notes");
        Path emMinimizados = emNotes.resolve("minimizados");

        Note nota = Note.create();
        nota.text("nota que vai minimizar");
        store.save(nota);
        Check.that("nasce em notes", Files.exists(emNotes.resolve(nota.id() + ".html")));

        nota.visible(false);
        store.save(nota);
        Check.that("minimizada foi para minimizados/",
                Files.exists(emMinimizados.resolve(nota.id() + ".html")));
        Check.that("saiu de notes", !Files.exists(emNotes.resolve(nota.id() + ".html")));
        Check.that("o nome do arquivo nao mudou: continua .html",
                emMinimizados.resolve(nota.id() + ".html").getFileName().toString()
                        .endsWith(".html"));

        List<Note> recarregadas = new NoteStore(base).loadAll();
        Check.that("carrega da pasta de minimizadas", recarregadas.size() == 1);
        Check.that("volta marcada como minimizada", !recarregadas.get(0).visible());
        Check.that("texto intacto",
                recarregadas.get(0).text().equals("nota que vai minimizar"));

        Note voltando = recarregadas.get(0);
        voltando.visible(true);
        store.save(voltando);
        Check.that("voltou para notes", Files.exists(emNotes.resolve(nota.id() + ".html")));
        Check.that("saiu de minimizados",
                !Files.exists(emMinimizados.resolve(nota.id() + ".html")));
        Check.that("volta marcada como visivel",
                new NoteStore(base).loadAll().get(0).visible());

        // apagar tem de achar a nota nas duas pastas
        voltando.visible(false);
        store.save(voltando);
        Check.that("apagar minimizada funciona", store.delete(voltando));
        Check.that("saiu de minimizados",
                !Files.exists(emMinimizados.resolve(nota.id() + ".html")));
        Check.that("esta na lixeira", store.trashHasNotes());
    }

    /**
     * Notas em {@code .properties} das versoes anteriores: convertidas uma vez, e o arquivo
     * antigo arquivado em vez de apagado -- mudanca de formato nao pode ser caminho sem volta.
     */
    private static void formatoAntigo() throws Exception {
        Check.grupo("Conversao do formato .properties");
        Path base = Files.createTempDirectory("recados-conversao");
        NoteStore store = new NoteStore(base);
        Path notes = base.resolve("notes");

        Files.writeString(notes.resolve("antiga.properties"),
                "createdAt=1700000000000\ntext=nota da versao anterior\n"
                        + "html=<html><body><b>negrito antigo</b></body></html>\n"
                        + "x=42\ny=84\nwidth=300\nheight=200\ncolor=Rosa\n"
                        + "alwaysOnTop=false\nvisible=false\n");

        List<Note> notas = store.loadAll();
        Check.that("a nota antiga carregou", notas.size() == 1);
        Note nota = notas.get(0);
        Check.that("id veio do nome do arquivo", nota.id().equals("antiga"));
        Check.that("formatacao preservada", nota.html().contains("<b>negrito antigo</b>"));
        Check.that("geometria preservada", nota.x() == 42 && nota.y() == 84
                && nota.width() == 300 && nota.height() == 200);
        Check.that("cor preservada", nota.palette().name().equals("Rosa"));
        Check.that("fixar no topo preservado", !nota.alwaysOnTop());
        Check.that("visible=false virou a pasta de minimizadas", !nota.visible());
        Check.that("gravou o .html na pasta certa",
                Files.exists(notes.resolve("minimizados").resolve("antiga.html")));
        Check.that("o .properties saiu de notes",
                !Files.exists(notes.resolve("antiga.properties")));
        Check.that("o .properties foi arquivado em legado/",
                Files.exists(base.resolve("legado").resolve("antiga.properties")));

        Check.that("segunda leitura ja e do .html", new NoteStore(base).loadAll().size() == 1);
        Check.that("nao converteu duas vezes",
                new NoteStore(base).loadAll().get(0).html().contains("<b>negrito antigo</b>"));
    }

    /**
     * Gravacao que nao completa tem de <b>avisar</b>, para a janela tentar de novo. Isto
     * apareceu de verdade: no Windows a troca atomica falha enquanto outro processo estiver
     * com o arquivo aberto por um instante (antivirus, indexador), e como o resultado era
     * ignorado, a nota perdia o que o usuario tinha escrito, em silencio.
     *
     * <p>Para simular a falha sem depender de antivirus, o destino e uma <b>pasta com algo
     * dentro</b>, com o nome do arquivo da nota: nenhum move consegue substituir isso. (Pasta
     * vazia nao serve: o Windows troca uma dessas por arquivo sem reclamar.)
     */
    private static void gravacaoQueFalha() throws Exception {
        Check.grupo("Gravacao que falha avisa");
        Path base = Files.createTempDirectory("recados-falha");
        NoteStore store = new NoteStore(base);

        Note nota = Note.create();
        nota.text("nao pode se perder em silencio");
        Check.that("gravacao normal devolve true", store.save(nota));

        Path obstaculo = base.resolve("notes").resolve(nota.id() + ".html");
        Files.delete(obstaculo);
        Files.createDirectory(obstaculo);
        Files.writeString(obstaculo.resolve("ocupado.txt"), "nao da para substituir");

        Check.that("gravacao impossivel devolve false", !store.save(nota));
        Check.that("e nao deixa arquivo temporario para tras",
                !Files.exists(base.resolve("notes").resolve(nota.id() + ".html.tmp")));
    }

    private static void lixeira() throws Exception {
        Check.grupo("Lixeira");
        Path base = Files.createTempDirectory("recados-lixeira");
        NoteStore store = new NoteStore(base);

        Check.that("lixeira nao existe antes de apagar", !Files.exists(base.resolve("trash")));
        Check.that("trashHasNotes falso no inicio", !store.trashHasNotes());

        Note nota = Note.create();
        nota.text("linha um\nlinha dois");
        store.save(nota);
        Check.that("nota salva", store.loadAll().size() == 1);

        Check.that("delete devolveu true", store.delete(nota));
        Check.that("saiu de notes", store.loadAll().isEmpty());
        Check.that("trashHasNotes verdadeiro", store.trashHasNotes());

        List<Path> naLixeira;
        try (var files = Files.list(base.resolve("trash"))) {
            naLixeira = files.toList();
        }
        Check.that("um arquivo na lixeira", naLixeira.size() == 1);
        Check.that("texto multilinha preservado",
                Files.readString(naLixeira.get(0)).contains("linha um<br>linha dois"));
        Check.that("nome tem carimbo de tempo", naLixeira.get(0).getFileName().toString()
                .matches(nota.id() + "-\\d+\\.html"));

        // restaurar e mover de volta, sem renomear
        Path restaurada = base.resolve("notes").resolve(nota.id() + ".html");
        Files.move(naLixeira.get(0), restaurada);
        List<Note> recarregadas = store.loadAll();
        Check.that("restaurada carrega", recarregadas.size() == 1);
        Check.that("texto intacto depois de restaurar",
                recarregadas.get(0).text().equals("linha um\nlinha dois"));

        Check.that("segundo delete devolveu true", store.delete(recarregadas.get(0)));
        try (var files = Files.list(base.resolve("trash"))) {
            Check.that("a copia anterior nao foi sobrescrita", files.count() == 1);
        }
        Check.that("delete de nota inexistente devolve true", store.delete(Note.create()));
    }

    /**
     * Nota em branco nao vai para a lixeira: nao ha o que recuperar, e arquivo vazio ali so
     * daria trabalho de limpar depois. Nota com conteudo continua indo.
     */
    private static void emBranco() throws Exception {
        Check.grupo("Nota em branco nao vai para a lixeira");
        Path base = Files.createTempDirectory("recados-branco");
        NoteStore store = new NoteStore(base);

        Note vazia = Note.create();
        store.save(vazia);
        Path arquivo = base.resolve("notes").resolve(vazia.id() + ".html");
        Check.that("a nota vazia foi gravada", Files.exists(arquivo));

        Check.that("deleteForever devolveu true", store.deleteForever(vazia));
        Check.that("o arquivo sumiu", !Files.exists(arquivo));
        Check.that("nada foi para a lixeira", !store.trashHasNotes());
        Check.that("nao aparece ao recarregar", store.loadAll().isEmpty());

        // e a mesma coisa para nota minimizada em branco
        Note vaziaMinimizada = Note.create();
        vaziaMinimizada.visible(false);
        store.save(vaziaMinimizada);
        Check.that("apaga de vez tambem em minimizados/",
                store.deleteForever(vaziaMinimizada));
        Check.that("continua sem lixeira", !store.trashHasNotes());

        Check.that("deleteForever de nota inexistente devolve true",
                store.deleteForever(Note.create()));

        // com conteudo, o caminho e a lixeira
        Note comTexto = Note.create();
        comTexto.text("isso nao pode sumir de vez");
        store.save(comTexto);
        Check.that("nota com conteudo vai para a lixeira", store.delete(comTexto));
        Check.that("e esta la", store.trashHasNotes());
    }

    private static void migracao() throws Exception {
        Check.grupo("Migracao de ~/.postit para ~/.recados");
        Path home = Files.createTempDirectory("recados-home");
        Path antiga = home.resolve(".postit");
        Path nova = home.resolve(".recados");

        NoteStore legado = new NoteStore(antiga);
        Note nota = Note.create();
        nota.text("nota de antes da renomeacao");
        legado.save(nota);
        Note apagada = Note.create();
        apagada.text("essa estava na lixeira");
        legado.save(apagada);
        legado.delete(apagada);

        String homeOriginal = System.getProperty("user.home");
        try {
            System.setProperty("user.home", home.toString());
            NoteStore store = new NoteStore();
            List<Note> notas = store.loadAll();

            Check.that("pasta nova existe", Files.isDirectory(nova));
            Check.that("pasta antiga sumiu", !Files.exists(antiga));
            Check.that("store aponta para a nova", store.baseDir().equals(nova));
            Check.that("a nota veio junto", notas.size() == 1);
            Check.that("texto intacto", notas.size() == 1
                    && notas.get(0).text().equals("nota de antes da renomeacao"));
            Check.that("lixeira veio junto", store.trashHasNotes());

            Check.that("segunda execucao continua na nova",
                    new NoteStore().baseDir().equals(nova));

            // com as duas pastas, a nova manda e a antiga fica intocada
            Files.createDirectories(antiga.resolve("notes"));
            Path intruso = antiga.resolve("notes").resolve("naodevecarregar.html");
            Files.writeString(intruso, "<html><body>nao deveria aparecer</body></html>");
            NoteStore terceira = new NoteStore();
            Check.that("com as duas, usa a nova", terceira.baseDir().equals(nova));
            Check.that("nao puxou nota da antiga", terceira.loadAll().size() == 1);
            Check.that("antiga preservada", Files.exists(intruso));
        } finally {
            System.setProperty("user.home", homeOriginal);
        }
    }

    /**
     * A cor e gravada pelo nome. Se voltar a ser posicao, reordenar a paleta repinta nota
     * gravada -- foi por isso que esta checagem existe.
     */
    private static void cores() throws Exception {
        Check.grupo("Cores");
        Path base = Files.createTempDirectory("recados-cores");
        NoteStore store = new NoteStore(base);

        Check.that("a cor padrao e azul", Palette.at(0).name().equals("Azul"));
        Check.that("amarelo continua disponivel", Palette.indexOf("Amarelo") >= 0);

        Note nota = Note.create();
        Check.that("nota nova nasce azul", nota.palette().name().equals("Azul"));

        nota.colorIndex(Palette.indexOf("Verde"));
        store.save(nota);
        Check.that("cor gravada pelo nome",
                Files.readString(base.resolve("notes").resolve(nota.id() + ".html"))
                        .contains("content=\"Verde\""));
        Check.that("cor volta igual do disco",
                store.loadAll().get(0).palette().name().equals("Verde"));

        // arquivo da versao em que a cor era indice: 0 era Amarelo, 3 era Azul
        gravarLegado(base, "legado-amarelo", "colorIndex=0");
        gravarLegado(base, "legado-azul", "colorIndex=3");
        List<Note> antigas = store.loadAll();
        Check.that("indice antigo 0 continua Amarelo", antigas.stream()
                .anyMatch(n -> n.id().equals("legado-amarelo")
                        && n.palette().name().equals("Amarelo")));
        Check.that("indice antigo 3 continua Azul", antigas.stream()
                .anyMatch(n -> n.id().equals("legado-azul")
                        && n.palette().name().equals("Azul")));

        // cor que nao existe mais nao pode quebrar a leitura
        gravarLegado(base, "cor-inexistente", "color=Turquesa");
        Check.that("cor desconhecida cai no padrao", store.loadAll().stream()
                .anyMatch(n -> n.id().equals("cor-inexistente")
                        && n.palette().name().equals("Azul")));
    }

    private static void gravarLegado(Path base, String id, String linhaDeCor) throws Exception {
        Files.writeString(base.resolve("notes").resolve(id + ".properties"),
                "createdAt=1700000000000\ntext=nota antiga\n" + linhaDeCor + "\n");
    }
}
