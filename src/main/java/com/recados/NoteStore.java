package com.recados;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Persistencia das notas em {@code ~/.recados/notes/<id>.html}.
 *
 * <p>Um arquivo por nota, e um arquivo <b>HTML de verdade</b>: abre no navegador com dois
 * cliques, da para arrastar para um e-mail e da para ler a olho nu. Os metadados (posicao,
 * tamanho, cor) moram em {@code <meta name="recados:...">} no {@code <head>} -- e nao em
 * comentario, porque comentario e a primeira coisa que editor, minificador ou sanitizador
 * tira sem avisar, enquanto {@code <meta name>} e o lugar que o proprio padrao reserva para
 * isto. O nome do arquivo e o id da nota.
 *
 * <p>O estado de minimizada nao e um metadado: e a <b>pasta</b>. Nota na tela fica em
 * {@code notes/}, nota minimizada em {@code notes/minimizados/}, com o mesmo nome de arquivo.
 * Uma verdade so, visivel no explorador, e o arquivo continua sendo {@code .html} nos dois
 * casos -- sufixo no nome (algo como {@code .html.minimizado}) tiraria a associacao do
 * Windows e mataria justamente o duplo clique.
 */
public final class NoteStore {

    private static final String EXTENSION = ".html";

    /** Formato das versoes anteriores a esta, lido uma vez e convertido. */
    private static final String LEGACY_EXTENSION = ".properties";

    /** Os metadados no {@code <head>}: {@code <meta name="recados:width" content="280">}. */
    private static final Pattern META = Pattern.compile(
            "(?is)<meta\\s+name=\"recados:([a-z0-9-]+)\"\\s+content=\"([^\"]*)\"\\s*/?>");

    /** Tags que valem uma quebra de linha ao extrair o texto puro do HTML. */
    private static final Pattern LINE_BREAKS = Pattern.compile(
            "(?i)<br\\s*/?>|</p>|</li>|</div>|</tr>|</h[1-6]>");

    private static final Pattern TAGS = Pattern.compile("(?s)<[^>]*>");

    private final Path dir;
    private final Path minimizedDir;
    private final Path trashDir;
    private final Path legacyDir;

    public NoteStore() {
        this(defaultBaseDir());
    }

    /**
     * {@code ~/.recados}, migrando de {@code ~/.postit} na primeira execucao depois da
     * troca de nome do projeto. Se a migracao falhar, continua usando a pasta antiga:
     * comecar de uma pasta vazia pareceria que as notas sumiram.
     */
    private static Path defaultBaseDir() {
        Path home = Path.of(System.getProperty("user.home"));
        Path base = home.resolve(".recados");
        Path legacy = home.resolve(".postit");
        if (!Files.exists(base) && Files.isDirectory(legacy)) {
            try {
                Files.move(legacy, base);
                System.out.println("Notas migradas de " + legacy + " para " + base);
            } catch (IOException e) {
                System.err.println("Nao foi possivel migrar " + legacy + " para " + base
                        + " (" + e.getMessage() + "); seguindo com a pasta antiga.");
                return legacy;
            }
        }
        return base;
    }

    public NoteStore(Path baseDir) {
        this.dir = baseDir.resolve("notes");
        this.minimizedDir = dir.resolve("minimizados");
        this.trashDir = baseDir.resolve("trash");
        this.legacyDir = baseDir.resolve("legado");
        try {
            Files.createDirectories(minimizedDir); // cria notes/ junto
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel criar " + minimizedDir, e);
        }
        // lixeira e legado nascem so quando houver o que colocar dentro
    }

    public Path baseDir() {
        return dir.getParent();
    }

    public Path notesDir() {
        return dir;
    }

    /** Onde ficam as notas minimizadas: mesmo nome de arquivo, outra pasta. */
    public Path minimizedDir() {
        return minimizedDir;
    }

    public Path trashDir() {
        return trashDir;
    }

    /** Todas as notas salvas, das duas pastas, da mais antiga para a mais recente. */
    public List<Note> loadAll() {
        Map<String, Note> byId = new LinkedHashMap<>();
        readDir(dir, true, byId);
        readDir(minimizedDir, false, byId);
        convertLegacyFiles(byId);

        List<Note> notes = new ArrayList<>(byId.values());
        notes.sort(Comparator.comparingLong(Note::createdAt));
        return notes;
    }

    private void readDir(Path folder, boolean visible, Map<String, Note> byId) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .sorted()
                    .forEach(p -> {
                        Note note = read(p, visible);
                        // o mesmo id nas duas pastas nao deveria acontecer (mudar de estado e
                        // um move atomico); se acontecer, a de notes/ manda e a outra e ignorada
                        if (note != null) {
                            byId.putIfAbsent(note.id(), note);
                        }
                    });
        } catch (IOException e) {
            System.err.println("Falha ao listar " + folder + ": " + e.getMessage());
        }
    }

    private Note read(Path file, boolean visible) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Ignorando nota ilegivel " + file.getFileName() + ": " + e.getMessage());
            return null;
        }
        Map<String, String> meta = readMeta(content);
        String body = body(content);

        Note note = new Note(idOf(file, EXTENSION), createdAt(meta.get("created-at")));
        note.html("<html><body>" + body + "</body></html>");
        note.text(htmlToPlainText(body));
        note.location(parseInt(meta.get("x"), 0), parseInt(meta.get("y"), 0));
        note.size(parseInt(meta.get("width"), Note.DEFAULT_WIDTH),
                parseInt(meta.get("height"), Note.DEFAULT_HEIGHT));
        note.colorIndex(colorIndex(meta.get("color")));
        note.alwaysOnTop(!"false".equalsIgnoreCase(orEmpty(meta.get("always-on-top")).strip()));
        note.visible(visible); // vem da pasta, nao do arquivo
        return note;
    }

    /**
     * Grava em arquivo temporario e move, para que uma falha no meio nao deixe a nota
     * truncada. Se a nota mudou de estado (minimizou ou voltou), o arquivo e <b>movido</b>
     * antes de ser reescrito: o move e atomico, entao a nota existe em exatamente um lugar
     * em qualquer instante. Gravar no destino e depois apagar a origem deixaria uma janela
     * em que um desligamento no meio produz duas notas com o mesmo id.
     */
    public void save(Note note) {
        Path target = folderFor(note.visible()).resolve(note.id() + EXTENSION);
        Path other = folderFor(!note.visible()).resolve(note.id() + EXTENSION);
        if (!Files.exists(target) && Files.exists(other)) {
            try {
                replace(other, target);
            } catch (IOException e) {
                System.err.println("Falha ao mover a nota " + note.id() + " para " + target
                        + ": " + e.getMessage());
                return; // gravar no outro lugar deixaria o estado mentindo
            }
        }

        Path temp = target.resolveSibling(note.id() + EXTENSION + ".tmp");
        try {
            Files.writeString(temp, render(note), StandardCharsets.UTF_8);
            replace(temp, target);
        } catch (IOException e) {
            System.err.println("Falha ao salvar nota " + note.id() + ": " + e.getMessage());
        }
    }

    /**
     * Troca um arquivo pelo outro sem que exista um instante em que a nota nao esta em lugar
     * nenhum. {@code REPLACE_EXISTING} sozinho nao garante isso: no Windows ele apaga o
     * destino e depois renomeia, e quem estiver lendo a pasta nesse meio -- o proprio Recados
     * abrindo, um backup, o explorador -- ve a nota desaparecida. Com {@code ATOMIC_MOVE} a
     * troca e uma operacao so. Se o sistema de arquivos nao suportar, cai no comportamento
     * antigo, que e melhor que nao gravar.
     */
    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path folderFor(boolean visible) {
        return visible ? dir : minimizedDir;
    }

    // ------------------------------------------------------------------ apagar

    /**
     * Manda a nota para a lixeira em vez de remover o arquivo, para que apagar por engano
     * deixe de ser definitivo. O carimbo de tempo no nome evita que apagar duas notas com
     * o mesmo id (nota restaurada e apagada de novo) sobrescreva a copia anterior.
     *
     * @return {@code false} se nao deu para mover -- nesse caso a nota fica onde esta,
     *         e volta no proximo inicio, em vez de sumir sem aviso.
     */
    public boolean delete(Note note) {
        Path source = locate(note);
        if (source == null) {
            return true; // nota que nunca chegou ao disco
        }
        try {
            Files.createDirectories(trashDir);
            Path target = trashDir.resolve(note.id() + "-" + System.currentTimeMillis() + EXTENSION);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            System.err.println("Falha ao mover nota " + note.id() + " para a lixeira: "
                    + e.getMessage());
            return false;
        }
    }

    /**
     * Apaga de vez, sem passar pela lixeira. Usado so para nota <b>em branco</b>: guardar
     * uma nota sem conteudo nenhum na lixeira nao protege ninguem de nada, e enche a pasta
     * de arquivos vazios que o usuario teria de limpar a mao.
     */
    public boolean deleteForever(Note note) {
        Path source = locate(note);
        if (source == null) {
            return true;
        }
        try {
            Files.delete(source);
            return true;
        } catch (IOException e) {
            System.err.println("Falha ao apagar nota " + note.id() + ": " + e.getMessage());
            return false;
        }
    }

    /** Onde o arquivo da nota esta agora, ou {@code null} se ela nunca foi gravada. */
    private Path locate(Note note) {
        Path onScreen = dir.resolve(note.id() + EXTENSION);
        if (Files.exists(onScreen)) {
            return onScreen;
        }
        Path minimized = minimizedDir.resolve(note.id() + EXTENSION);
        return Files.exists(minimized) ? minimized : null;
    }

    /** Se ha algo para o usuario recuperar. */
    public boolean trashHasNotes() {
        if (!Files.isDirectory(trashDir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(trashDir)) {
            return files.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ o arquivo

    /**
     * A nota como pagina HTML: metadados no {@code <head>}, texto no {@code <body>}. O
     * {@code <style>} leva a cor da paleta, entao o arquivo aberto no navegador tem a cara
     * da nota -- e como o {@code <head>} nunca e lido de volta como conteudo, isso nao
     * realimenta o documento.
     */
    private String render(Note note) {
        Palette palette = note.palette();
        StringBuilder out = new StringBuilder(512);
        out.append("<!DOCTYPE html>\n<html lang=\"pt-BR\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<title>").append(escapeHtml(note.title())).append("</title>\n");
        meta(out, "created-at", Instant.ofEpochMilli(note.createdAt()).toString());
        meta(out, "x", Integer.toString(note.x()));
        meta(out, "y", Integer.toString(note.y()));
        meta(out, "width", Integer.toString(note.width()));
        meta(out, "height", Integer.toString(note.height()));
        // pelo nome, e nao pela posicao: reordenar a paleta nao pode repintar nota gravada
        meta(out, "color", palette.name());
        meta(out, "always-on-top", Boolean.toString(note.alwaysOnTop()));
        out.append("<style>\n")
                .append("body { background: ").append(hex(palette.body()))
                .append("; color: ").append(hex(palette.text()))
                .append("; font-family: 'Segoe UI', sans-serif; font-size: 11pt;")
                .append(" margin: 0; padding: 14px; }\n")
                .append("a { color: #0B57D0; }\n") // o mesmo azul de link da nota
                .append("</style>\n")
                // aqui comentario e o certo: o recado e para o humano, nao para o programa
                .append("<!-- Nao renomeie este arquivo: o id do recado vem do nome dele. -->\n")
                .append("</head>\n<body>\n")
                .append(contentOf(note))
                .append("\n</body>\n</html>\n");
        return out.toString();
    }

    /**
     * O que vai dentro do {@code <body>}. Normalmente e o HTML do documento, mas a nota pode
     * chegar aqui com {@code html} vazio -- nota nova gravada antes de qualquer edicao, ou
     * nota antiga sendo convertida -- e nesses casos o conteudo vem do texto puro. Sem este
     * caminho, gravar uma nota dessas produziria um arquivo de corpo vazio.
     */
    private static String contentOf(Note note) {
        String html = body(note.html());
        if (!html.isBlank()) {
            return html;
        }
        return body(HtmlText.plainToHtml(note.text()));
    }

    private static void meta(StringBuilder out, String name, String value) {
        out.append("<meta name=\"recados:").append(name)
                .append("\" content=\"").append(escapeHtml(value)).append("\">\n");
    }

    private static String hex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Map<String, String> readMeta(String content) {
        Map<String, String> meta = new LinkedHashMap<>();
        Matcher matcher = META.matcher(content);
        while (matcher.find()) {
            meta.put(matcher.group(1).toLowerCase(), unescapeHtml(matcher.group(2)));
        }
        return meta;
    }

    /** O conteudo do {@code <body>}; o arquivo inteiro se nao houver body reconhecivel. */
    private static String body(String content) {
        return HtmlText.body(content);
    }

    /**
     * O texto puro que alimenta a lista da bandeja e o titulo da nota. Antes vinha gravado
     * junto no arquivo, em {@code text=}; num arquivo HTML de verdade ele e derivado, para
     * o arquivo nao ter duas versoes do mesmo conteudo podendo divergir.
     */
    static String htmlToPlainText(String html) {
        String text = LINE_BREAKS.matcher(html).replaceAll("\n");
        text = TAGS.matcher(text).replaceAll("");
        text = unescapeHtml(text);
        text = text.replace("\r", "").replaceAll("[ \t]+\n", "\n").replaceAll("\n{3,}", "\n\n");
        return text.strip();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String unescapeHtml(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&"); // por ultimo: senao "&amp;lt;" viraria "<"
    }

    // ------------------------------------------------------- formato anterior

    /**
     * Converte as notas em {@code .properties} das versoes anteriores. A conversao acontece
     * uma vez, na leitura, e o arquivo antigo vai para {@code legado/} em vez de ser apagado:
     * nenhuma mudanca de formato deve ser um caminho sem volta. Se a conversao falhar em
     * qualquer ponto, a nota ainda e devolvida -- ela aparece na tela, e o arquivo antigo
     * fica onde estava para tentar de novo no proximo inicio.
     */
    private void convertLegacyFiles(Map<String, Note> byId) {
        List<Path> legacyFiles = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(LEGACY_EXTENSION))
                    .sorted().forEach(legacyFiles::add);
        } catch (IOException e) {
            return;
        }
        for (Path file : legacyFiles) {
            String id = idOf(file, LEGACY_EXTENSION);
            boolean isNew = !byId.containsKey(id);
            if (isNew) {
                Note note = readLegacy(file, id);
                if (note == null) {
                    continue;
                }
                byId.put(id, note);
                save(note);
            }
            archiveLegacy(file);
        }
    }

    private void archiveLegacy(Path file) {
        try {
            Files.createDirectories(legacyDir);
            Files.move(file, legacyDir.resolve(file.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Nota convertida, mas nao deu para arquivar " + file.getFileName()
                    + " em " + legacyDir + ": " + e.getMessage());
        }
    }

    private Note readLegacy(Path file, String id) {
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("Ignorando nota ilegivel " + file.getFileName() + ": " + e.getMessage());
            return null;
        }
        Note note = new Note(id, parseLong(props.getProperty("createdAt"), System.currentTimeMillis()));
        note.text(props.getProperty("text", ""));
        note.rtf(props.getProperty("rtf", ""));
        note.html(props.getProperty("html", ""));
        if (note.html().isBlank()) {
            // nota da versao em RTF: a formatacao tem de ser convertida aqui, senao o
            // arquivo .html sairia com o texto puro e o negrito se perderia na conversao
            note.html(HtmlText.rtfToHtml(note.rtf()));
        }
        if (!note.html().isBlank()) {
            // Havendo HTML, o texto puro sai dele, como no formato novo. O {@code text=}
            // antigo era gravado sem quebras de linha -- a nota inteira numa linha so --, e
            // aproveita-lo daria titulo ruim numa nota que agora tem como ter titulo bom.
            note.text(htmlToPlainText(body(note.html())));
        }
        note.location(parseInt(props.getProperty("x"), 0), parseInt(props.getProperty("y"), 0));
        note.size(parseInt(props.getProperty("width"), Note.DEFAULT_WIDTH),
                parseInt(props.getProperty("height"), Note.DEFAULT_HEIGHT));
        note.colorIndex(legacyColorIndex(props));
        note.alwaysOnTop(Boolean.parseBoolean(props.getProperty("alwaysOnTop", "true")));
        note.visible(Boolean.parseBoolean(props.getProperty("visible", "true")));
        return note;
    }

    /**
     * A ordem da paleta na epoca em que a cor era gravada como indice, para nota antiga
     * continuar com a cor que tinha. Nao mexa: e a chave de leitura desses arquivos.
     */
    private static final String[] LEGACY_COLOR_ORDER =
            {"Amarelo", "Rosa", "Verde", "Azul", "Laranja", "Lilas"};

    /** Prefere o nome; cai no indice antigo quando o arquivo e de antes dessa mudanca. */
    private static int legacyColorIndex(Properties props) {
        String name = props.getProperty("color");
        if (name != null && !name.isBlank()) {
            return colorIndex(name);
        }
        int legacy = parseInt(props.getProperty("colorIndex"), -1);
        if (legacy >= 0 && legacy < LEGACY_COLOR_ORDER.length) {
            return colorIndex(LEGACY_COLOR_ORDER[legacy]);
        }
        return 0;
    }

    /** Cor que nao existe mais cai na padrao em vez de quebrar a leitura da nota. */
    private static int colorIndex(String name) {
        if (name == null || name.isBlank()) {
            return 0;
        }
        int index = Palette.indexOf(name.strip());
        return index >= 0 ? index : 0;
    }

    // ------------------------------------------------------------------ utilidades

    private static String idOf(Path file, String extension) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - extension.length());
    }

    private static long createdAt(String value) {
        if (value == null || value.isBlank()) {
            return System.currentTimeMillis();
        }
        try {
            return Instant.parse(value.strip()).toEpochMilli();
        } catch (DateTimeParseException e) {
            return parseLong(value, System.currentTimeMillis());
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value == null ? fallback : Long.parseLong(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
