import com.recados.Note;
import com.recados.NoteFrame;
import com.recados.NoteStore;
import com.recados.RecadosApp;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.rtf.RTFEditorKit;

/** A formatacao tem que sobreviver ao disco e ao ciclo abrir/gravar da janela. */
public final class RichTextChecks {

    private static final String TEXTO = "negrito e normal";

    public static void run() throws Exception {
        Check.grupo("Texto rico");

        Path base = Files.createTempDirectory("recados-rtf");
        NoteStore store = new NoteStore(base);
        RecadosApp app = new RecadosApp(store);

        String rtf = rtfComNegrito();
        Check.that("o RTF de partida marca negrito", rtf.contains("\\b"));

        Note nota = Note.create();
        nota.text(TEXTO);
        nota.rtf(rtf);
        store.save(nota);

        List<Note> recarregadas = new NoteStore(base).loadAll();
        Check.that("nota recarregou", recarregadas.size() == 1);
        Note doDisco = recarregadas.get(0);
        Check.that("RTF sobreviveu ao disco", doDisco.rtf().contains("\\b"));
        Check.that("texto puro tambem foi gravado", doDisco.text().startsWith("negrito e normal"));

        // abre na janela de verdade e grava de novo: le RTF, escreve RTF
        NoteFrame[] frame = new NoteFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new NoteFrame(doDisco, app);
            frame[0].setVisible(true);
        });
        SwingUtilities.invokeAndWait(() -> frame[0].flush());

        Check.that("negrito sobreviveu ao ciclo abrir/gravar", doDisco.rtf().contains("\\b"));
        Check.that("texto sobreviveu ao ciclo abrir/gravar",
                doDisco.text().startsWith("negrito e normal"));
        Check.that("titulo do menu usa o texto puro, sem marcacao",
                doDisco.title().startsWith("negrito e normal"));

        List<Note> depois = new NoteStore(base).loadAll();
        Check.that("o que esta no disco tem os dois formatos", depois.size() == 1
                && !depois.get(0).rtf().isBlank()
                && !depois.get(0).text().isBlank());

        // nota antiga, so com texto puro, tem que abrir sem reclamar
        Note antiga = Note.create();
        antiga.text("nota de antes do texto rico");
        store.save(antiga);
        Check.that("nota sem RTF continua sem RTF", antiga.rtf().isEmpty());
        NoteFrame[] outra = new NoteFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            outra[0] = new NoteFrame(antiga, app);
            outra[0].setVisible(true);
        });
        SwingUtilities.invokeAndWait(() -> outra[0].flush());
        Check.that("texto puro preservado ao abrir nota antiga",
                antiga.text().startsWith("nota de antes do texto rico"));
        Check.that("nota antiga ganha formatacao ao ser gravada", !antiga.rtf().isBlank());

        SwingUtilities.invokeAndWait(() -> {
            frame[0].dispose();
            outra[0].dispose();
        });
    }

    /** "negrito" em negrito, " e normal" sem. */
    private static String rtfComNegrito() throws Exception {
        DefaultStyledDocument doc = new DefaultStyledDocument();
        SimpleAttributeSet bold = new SimpleAttributeSet();
        StyleConstants.setBold(bold, true);
        doc.insertString(0, "negrito", bold);
        doc.insertString(doc.getLength(), " e normal", new SimpleAttributeSet());
        // RTFEditorKit e 8-bit: Writer nao serve, e Latin-1 preserva os bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new RTFEditorKit().write(out, doc, 0, doc.getLength());
        return out.toString(StandardCharsets.ISO_8859_1);
    }
}
