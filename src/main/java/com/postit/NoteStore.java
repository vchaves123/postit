package com.postit;

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
 * Persistencia das notas em {@code ~/.postit/notes/<id>.properties}.
 * Um arquivo por nota: escrever uma nunca corrompe as outras. Sem dependencias externas --
 * {@link Properties} ja escapa quebras de linha, entao texto multilinha sobrevive ao round-trip.
 */
public final class NoteStore {

    private static final String EXTENSION = ".properties";

    private final Path dir;

    public NoteStore() {
        this(Path.of(System.getProperty("user.home"), ".postit"));
    }

    public NoteStore(Path baseDir) {
        this.dir = baseDir.resolve("notes");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel criar " + dir, e);
        }
    }

    public Path baseDir() {
        return dir.getParent();
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
        note.location(parseInt(props.getProperty("x"), 0), parseInt(props.getProperty("y"), 0));
        note.size(parseInt(props.getProperty("width"), Note.DEFAULT_WIDTH),
                parseInt(props.getProperty("height"), Note.DEFAULT_HEIGHT));
        note.colorIndex(parseInt(props.getProperty("colorIndex"), 0));
        note.alwaysOnTop(Boolean.parseBoolean(props.getProperty("alwaysOnTop", "true")));
        return note;
    }

    /** Grava em arquivo temporario e move, para que uma falha no meio nao deixe a nota truncada. */
    public void save(Note note) {
        Properties props = new Properties();
        props.setProperty("createdAt", Long.toString(note.createdAt()));
        props.setProperty("text", note.text());
        props.setProperty("x", Integer.toString(note.x()));
        props.setProperty("y", Integer.toString(note.y()));
        props.setProperty("width", Integer.toString(note.width()));
        props.setProperty("height", Integer.toString(note.height()));
        props.setProperty("colorIndex", Integer.toString(note.colorIndex()));
        props.setProperty("alwaysOnTop", Boolean.toString(note.alwaysOnTop()));

        Path target = dir.resolve(note.id() + EXTENSION);
        Path temp = dir.resolve(note.id() + EXTENSION + ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                props.store(writer, "postit note");
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Falha ao salvar nota " + note.id() + ": " + e.getMessage());
        }
    }

    public void delete(Note note) {
        try {
            Files.deleteIfExists(dir.resolve(note.id() + EXTENSION));
        } catch (IOException e) {
            System.err.println("Falha ao apagar nota " + note.id() + ": " + e.getMessage());
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
