package com.postit;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Post-it para a area de trabalho: cada nota e uma janelinha colorida que se lembra
 * de onde estava, do tamanho e da cor. Ponto de entrada do aplicativo.
 */
public final class PostItApp implements NoteFrame.Host {

    private static final int CASCADE_STEP = 28;
    private static final int MARGIN = 60;

    private final NoteStore store;
    private final Map<String, NoteFrame> frames = new LinkedHashMap<>();
    private TrayIcon trayIcon;
    private int cascadeCount;

    /** Mantido em campo para o lock de instancia unica viver enquanto o processo viver. */
    private static FileChannel lockChannel;

    public PostItApp(NoteStore store) {
        this.store = store;
    }

    public static void main(String[] args) {
        NoteStore store = new NoteStore();
        if (!acquireSingleInstanceLock(store.baseDir())) {
            System.out.println("postit ja esta em execucao.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            applyLookAndFeel();
            new PostItApp(store).start();
        });
    }

    private void start() {
        List<Note> notes = store.loadAll();
        if (notes.isEmpty()) {
            notes = List.of(welcomeNote());
        }
        for (Note note : notes) {
            openFrame(note);
        }
        installTray();
    }

    private Note welcomeNote() {
        Note note = Note.create();
        note.text("""
                Bem-vindo ao postit!

                Ctrl+N  nova nota
                Ctrl+E  trocar a cor
                Ctrl+T  fixar/soltar no topo
                Ctrl+D  apagar esta nota
                Ctrl+Q  sair

                Arraste pela barra de cima,
                redimensione pelo canto de baixo.
                O texto salva sozinho.""");
        store.save(note);
        return note;
    }

    // ------------------------------------------------------------------ janelas

    private NoteFrame openFrame(Note note) {
        NoteFrame existing = frames.get(note.id());
        if (existing != null) {
            existing.focusText();
            return existing;
        }
        placeIfNeeded(note);
        NoteFrame frame = new NoteFrame(note, this);
        frames.put(note.id(), frame);
        frame.setVisible(true);
        refreshTrayMenu();
        return frame;
    }

    /** Posiciona a nota em cascata quando ela nao tem lugar valido em nenhuma tela. */
    private void placeIfNeeded(Note note) {
        if (note.x() != 0 || note.y() != 0) {
            if (isOnSomeScreen(note)) {
                return;
            }
        }
        Rectangle bounds = defaultScreenBounds();
        int shift = (cascadeCount++ % 8) * CASCADE_STEP;
        note.location(bounds.x + bounds.width - note.width() - MARGIN - shift,
                bounds.y + MARGIN + shift);
    }

    private static boolean isOnSomeScreen(Note note) {
        Rectangle rect = new Rectangle(note.x(), note.y(), note.width(), note.height());
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            for (GraphicsConfiguration config : device.getConfigurations()) {
                if (config.getBounds().intersects(rect)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Rectangle defaultScreenBounds() {
        return GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration().getBounds();
    }

    // ------------------------------------------------- NoteFrame.Host

    @Override
    public void newNote(NoteFrame origin) {
        Note note = Note.create();
        if (origin != null) {
            // herda a cor da nota de onde veio o comando
            note.colorIndex(origin.note().colorIndex());
        }
        store.save(note);
        openFrame(note).focusText();
    }

    @Override
    public void deleteNote(NoteFrame frame) {
        if (!frame.confirmDelete()) {
            return;
        }
        frames.remove(frame.note().id());
        store.delete(frame.note());
        frame.dispose();
        refreshTrayMenu();
        if (frames.isEmpty() && trayIcon == null) {
            // sem bandeja nao haveria como voltar: abre uma nota nova
            newNote(null);
        }
    }

    @Override
    public void saveNote(Note note) {
        store.save(note);
        refreshTrayMenu();
    }

    @Override
    public void showAll() {
        for (NoteFrame frame : new ArrayList<>(frames.values())) {
            frame.setVisible(true);
            frame.toFront();
        }
    }

    @Override
    public void quit() {
        for (NoteFrame frame : new ArrayList<>(frames.values())) {
            frame.flush();
        }
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        System.exit(0);
    }

    // ------------------------------------------------------------------ bandeja

    private void installTray() {
        if (!SystemTray.isSupported()) {
            return;
        }
        trayIcon = new TrayIcon(Icons.trayIcon(16), "postit");
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> showAll());
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            trayIcon = null;
            return;
        }
        refreshTrayMenu();
    }

    private void refreshTrayMenu() {
        if (trayIcon == null) {
            return;
        }
        PopupMenu menu = new PopupMenu();
        menu.add(menuItem("Nova nota", () -> newNote(null)));
        menu.add(menuItem("Mostrar todas", this::showAll));
        if (!frames.isEmpty()) {
            menu.addSeparator();
            for (NoteFrame frame : frames.values()) {
                menu.add(menuItem(frame.note().title(), frame::focusText));
            }
        }
        menu.addSeparator();
        menu.add(menuItem("Sobre", this::showAbout));
        menu.add(menuItem("Sair", this::quit));
        trayIcon.setPopupMenu(menu);
    }

    private static MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(null,
                "postit 1.0.0\nNotas na area de trabalho, em Java 21 + Swing.\n\nNotas salvas em:\n"
                        + store.baseDir(),
                "Sobre o postit", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------ infra

    private static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            // o look and feel padrao serve
        }
    }

    /** Evita duas instancias brigando pelos mesmos arquivos. */
    private static boolean acquireSingleInstanceLock(Path baseDir) {
        try {
            lockChannel = FileChannel.open(baseDir.resolve(".lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = lockChannel.tryLock();
            return lock != null;
        } catch (IOException e) {
            // sem lock disponivel: melhor abrir do que travar o usuario
            return true;
        }
    }
}
