import com.recados.Note;
import com.recados.NoteFrame;
import com.recados.NoteStore;
import com.recados.RecadosApp;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
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

/** Formatacao em HTML: ida e volta, listas, links, colagem formatada e notas antigas. */
public final class HtmlChecks {

    public static void run() throws Exception {
        idaEVolta();
        listasELinks();
        colagemFormatada();
        notasAntigas();
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
