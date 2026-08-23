package com.recados;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Persistencia das notas em {@code ~/.recados/notes/<id>.properties}.
 * Um arquivo por nota: escrever uma nunca corrompe as outras. Sem dependencias externas --
 * {@link Properties} ja escapa quebras de linha, entao texto multilinha sobrevive ao round-trip.
 */
public final class NoteStore {

    private static final String EXTENSION = ".properties";

    private final Path dir;
    private final Path trashDir;

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
        this.trashDir = baseDir.resolve("trash");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel criar " + dir, e);
        }
        // a lixeira e criada so quando alguem apaga a primeira nota
    }

    public Path baseDir() {
        return dir.getParent();
    }

    public Path notesDir() {
        return dir;
    }

    /** Todas as notas salvas, da mais antiga para a mais recente. */
    public List<Note> loadAll() {
        List<Note> notes = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(EXTENSION))
                    .forEach(p -> {
                        Note note = read(p);
                        if (note != null) {
                            notes.add(note);
                        }
                    });
        } catch (IOException e) {
            System.err.println("Falha ao listar notas: " + e.getMessage());
        }
        notes.sort(Comparator.comparingLong(Note::createdAt));
        return notes;
    }

    private Note read(Path file) {
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            System.err.println("Ignorando nota ilegivel " + file.getFileName() + ": " + e.getMessage());
            return null;
        }
        String name = file.getFileName().toString();
        String id = name.substring(0, name.length() - EXTENSION.length());
        Note note = new Note(id, parseLong(props.getProperty("createdAt"), System.currentTimeMillis()));
        note.text(props.getProperty("text", ""));
        note.rtf(props.getProperty("rtf", ""));
        note.html(props.getProperty("html", ""));
        note.location(parseInt(props.getProperty("x"), 0), parseInt(props.getProperty("y"), 0));
        note.size(parseInt(props.getProperty("width"), Note.DEFAULT_WIDTH),
                parseInt(props.getProperty("height"), Note.DEFAULT_HEIGHT));
        note.colorIndex(parseInt(props.getProperty("colorIndex"), 0));
        note.alwaysOnTop(Boolean.parseBoolean(props.getProperty("alwaysOnTop", "true")));
        note.visible(Boolean.parseBoolean(props.getProperty("visible", "true")));
        return note;
    }

    /** Grava em arquivo temporario e move, para que uma falha no meio nao deixe a nota truncada. */
    public void save(Note note) {
        Properties props = new Properties();
        props.setProperty("createdAt", Long.toString(note.createdAt()));
        props.setProperty("text", note.text());
        props.setProperty("html", note.html());
        props.setProperty("x", Integer.toString(note.x()));
        props.setProperty("y", Integer.toString(note.y()));
        props.setProperty("width", Integer.toString(note.width()));
        props.setProperty("height", Integer.toString(note.height()));
        props.setProperty("colorIndex", Integer.toString(note.colorIndex()));
        props.setProperty("alwaysOnTop", Boolean.toString(note.alwaysOnTop()));
        props.setProperty("visible", Boolean.toString(note.visible()));

        Path target = dir.resolve(note.id() + EXTENSION);
        Path temp = dir.resolve(note.id() + EXTENSION + ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                props.store(writer, "Recados note");
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Falha ao salvar nota " + note.id() + ": " + e.getMessage());
        }
    }

    /**
     * Manda a nota para a lixeira em vez de remover o arquivo, para que apagar por engano
     * deixe de ser definitivo. O carimbo de tempo no nome evita que apagar duas notas com
     * o mesmo id (nota restaurada e apagada de novo) sobrescreva a copia anterior.
     *
     * @return {@code false} se nao deu para mover -- nesse caso a nota fica onde esta,
     *         e volta no proximo inicio, em vez de sumir sem aviso.
     */
    public boolean delete(Note note) {
        Path source = dir.resolve(note.id() + EXTENSION);
        if (!Files.exists(source)) {
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

    public Path trashDir() {
        return trashDir;
    }

    /** Se ha algo para o usuario recuperar. */
    public boolean trashHasNotes() {
        if (!Files.isDirectory(trashDir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(trashDir)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(EXTENSION));
        } catch (IOException e) {
            return false;
        }
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
