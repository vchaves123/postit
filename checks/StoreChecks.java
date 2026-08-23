import com.recados.Note;
import com.recados.NoteStore;
import com.recados.Palette;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Lixeira e a migracao de ~/.postit para ~/.recados. */
public final class StoreChecks {

    public static void run() throws Exception {
        lixeira();
        migracao();
        cores();
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
                Files.readString(base.resolve("notes").resolve(nota.id() + ".properties"))
                        .contains("color=Verde"));
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
                Files.readString(naLixeira.get(0)).contains("linha um\\nlinha dois"));
        Check.that("nome tem carimbo de tempo", naLixeira.get(0).getFileName().toString()
                .matches(nota.id() + "-\\d+\\.properties"));

        // restaurar e mover de volta, sem renomear
        Path restaurada = base.resolve("notes").resolve(naLixeira.get(0).getFileName());
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
            Path intruso = antiga.resolve("notes").resolve("naodevecarregar.properties");
            Files.writeString(intruso, "text=nao deveria aparecer\n");
            NoteStore terceira = new NoteStore();
            Check.that("com as duas, usa a nova", terceira.baseDir().equals(nova));
            Check.that("nao puxou nota da antiga", terceira.loadAll().size() == 1);
            Check.that("antiga preservada", Files.exists(intruso));
        } finally {
            System.setProperty("user.home", homeOriginal);
        }
    }
}
