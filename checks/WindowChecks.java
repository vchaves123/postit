import com.recados.Note;
import com.recados.NoteFrame;
import com.recados.NoteStore;
import com.recados.RecadosApp;
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
        Check.that("arquivo continua em notes",
                Files.exists(base.resolve("notes").resolve(nota.id() + ".properties")));
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

        Path arquivo = base.resolve("notes").resolve(nota.id() + ".properties");
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

    private static NoteFrame abrir(Note nota, RecadosApp app) throws Exception {
        NoteFrame[] frame = new NoteFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new NoteFrame(nota, app);
            frame[0].setVisible(true);
        });
        return frame[0];
    }
}
