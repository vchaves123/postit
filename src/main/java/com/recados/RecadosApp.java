package com.recados;

import java.awt.AWTException;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Frame;
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
import javax.swing.JPopupMenu;
import javax.swing.ToolTipManager;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/**
 * Recados na area de trabalho: cada nota e uma janelinha colorida que se lembra
 * de onde estava, do tamanho e da cor. Ponto de entrada do aplicativo.
 */
public final class RecadosApp implements NoteFrame.Host {

    private static final int CASCADE_STEP = 28;
    private static final int MARGIN = 60;

    private final NoteStore store;

    /** Todas as notas conhecidas, na tela ou minimizadas -- e o que a bandeja lista. */
    private final Map<String, Note> notes = new LinkedHashMap<>();

    /** Somente as que estao com janela na tela. */
    private final Map<String, NoteFrame> frames = new LinkedHashMap<>();
    private TrayIcon trayIcon;
    private int cascadeCount;

    /** Mantido em campo para o lock de instancia unica viver enquanto o processo viver. */
    private static FileChannel lockChannel;

    public RecadosApp(NoteStore store) {
        this.store = store;
    }

    public static void main(String[] args) {
        Trace.instalar();
        NoteStore store = new NoteStore();
        if (!acquireSingleInstanceLock(store.baseDir())) {
            System.out.println("Recados ja esta em execucao.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            applyLookAndFeel();
            new RecadosApp(store).start();
        });
    }

    private void start() {
        List<Note> loaded = store.loadAll();
        if (loaded.isEmpty()) {
            loaded = List.of(welcomeNote());
        }
        for (Note note : loaded) {
            notes.put(note.id(), note);
        }
        installTray();

        for (Note note : notes.values()) {
            if (note.visible()) {
                openFrame(note);
            }
        }
        // sem bandeja nao ha como reabrir uma nota minimizada: mostra pelo menos uma
        if (frames.isEmpty() && trayIcon == null) {
            openFrame(notes.values().iterator().next());
        }
        refreshTrayMenu();
    }

    private Note welcomeNote() {
        Note note = Note.create();
        note.text("""
                Bem-vindo ao Recados!

                Ctrl+N  nova nota
                Ctrl+E  trocar a cor
                Ctrl+T  fixar/soltar no topo
                Ctrl+W  minimizar (nao apaga)
                Ctrl+D  apagar, com confirmacao
                Ctrl+Q  sair

                O botao - minimiza a nota;
                ela volta pela bandeja.

                A barra de baixo formata o texto:
                negrito, italico, sublinhado,
                lista, link e limpar formatacao.

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
        notes.putIfAbsent(note.id(), note);
        placeIfNeeded(note);
        NoteFrame frame = new NoteFrame(note, this);
        frames.put(note.id(), frame);
        frame.setVisible(true);
        if (!note.visible()) {
            note.visible(true);
            store.save(note);
        }
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
    public void closeNote(NoteFrame frame) {
        Note note = frame.note();
        if (trayIcon == null && frames.size() == 1) {
            // sem bandeja, minimizar a ultima nota deixaria o app sem porta de volta
            JOptionPane.showMessageDialog(frame,
                    "Esta e a ultima nota aberta e o icone da bandeja nao esta disponivel,\n"
                            + "entao nao haveria como reabri-la.\n\n"
                            + "Para encerrar o Recados, use Ctrl+Q.",
                    "Recados", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        note.visible(false);
        frames.remove(note.id());
        frame.closeWindow(); // grava a nota, ja com visible=false, e some da tela
        refreshTrayMenu();
    }

    /**
     * Nota em branco vai embora direto: sem perguntar, e sem passar pela lixeira. Nao ha o
     * que confirmar nem o que recuperar, e guardar arquivo vazio na lixeira so daria trabalho
     * de limpar depois. Nota com conteudo continua pedindo confirmacao e continua indo para
     * a lixeira.
     */
    @Override
    public void deleteNote(NoteFrame frame) {
        boolean blank = frame.isBlank();
        if (!blank && !frame.confirmDelete()) {
            return;
        }
        if (!(blank ? store.deleteForever(frame.note()) : store.delete(frame.note()))) {
            // nao fecha a janela: a nota continua no disco, e sumir da tela seria mentira
            JOptionPane.showMessageDialog(frame,
                    blank
                            ? "Nao foi possivel apagar o arquivo da nota em\n" + store.notesDir()
                                    + "\n\nA nota continua salva."
                            : "Nao foi possivel mover a nota para a lixeira em\n" + store.trashDir()
                                    + "\n\nA nota continua salva.",
                    "Recados", JOptionPane.ERROR_MESSAGE);
            return;
        }
        frames.remove(frame.note().id());
        notes.remove(frame.note().id());
        frame.discard();
        refreshTrayMenu();
        if (notes.isEmpty() && trayIcon == null) {
            // sem bandeja nao haveria como voltar: abre uma nota nova
            newNote(null);
        }
    }

    @Override
    public boolean saveNote(Note note) {
        boolean saved = store.save(note);
        refreshTrayMenu();
        return saved;
    }

    @Override
    public void openSettings(NoteFrame origin) {
        new SettingsDialog(origin, store).setVisible(true);
    }

    /**
     * Poe todas as notas na tela: desminimiza as que estao guardadas, tira do estado
     * iconificado as que o <b>Windows</b> minimizou (Win+D, "Mostrar area de trabalho",
     * troca de monitor) e traz de volta as que ficaram numa tela que nao existe mais.
     *
     * <p>Cada um desses tres casos ja fez as notas "sumirem" sem terem sido minimizadas por
     * ninguem, e o comando so tratava do primeiro: {@code toFront()} numa janela iconificada
     * pelo sistema nao a traz de volta, e uma janela em coordenada de monitor desligado
     * continua invisivel por mais que se peca foco para ela.
     */
    @Override
    public void showAll() {
        for (Note note : new ArrayList<>(notes.values())) {
            NoteFrame frame = openFrame(note);
            rescueIfLost(frame);
            frame.deiconify();
            frame.toFront();
        }
    }

    /** Nota parada onde nao ha tela nenhuma volta para a tela principal. */
    private void rescueIfLost(NoteFrame frame) {
        Note note = frame.note();
        if (isOnSomeScreen(note)) {
            return;
        }
        placeIfNeeded(note);
        frame.setLocation(note.x(), note.y());
        store.save(note);
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
        trayIcon = new TrayIcon(Icons.trayIcon(16), "Recados");
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
        // lista todas, nao so as na tela: e por aqui que uma nota minimizada volta
        if (!notes.isEmpty()) {
            menu.addSeparator();
            for (Note note : notes.values()) {
                String label = note.visible() ? note.title() : note.title() + "  (minimizada)";
                menu.add(menuItem(label, () -> openFrame(note).focusText()));
            }
        }
        menu.addSeparator();
        menu.add(menuItem("Configuracoes...", () -> openSettings(anyFrame())));
        menu.add(menuItem("Sobre", this::showAbout));
        menu.add(menuItem("Sair", this::quit));
        trayIcon.setPopupMenu(menu);
    }

    /** Qualquer nota aberta, para servir de dona do dialogo; {@code null} se nao houver nenhuma. */
    private NoteFrame anyFrame() {
        return frames.values().stream().findFirst().orElse(null);
    }

    private static MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(anyFrame(),
                "Recados 1.0.0\nNotas na area de trabalho, em Java 21 + Swing.\n\nNotas salvas em:\n"
                        + store.baseDir(),
                "Sobre o Recados", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------ infra

    private static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            // o look and feel padrao serve
        }

        // Tooltips e menus em janela nativa propria, nao desenhados dentro da nota: em janela
        // sem decoracao e sempre-no-topo, a versao leve deixa rastro sobre os botoes.
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);

        // Dialogo criado com pai null usa este frame escondido do Swing, que aparece na barra
        // de tarefas com o icone padrao do Java. Dando o nosso icone a ele, nenhuma janela do
        // Recados mostra o cafezinho -- nem se algum dialogo futuro esquecer de passar o pai.
        if (JOptionPane.getRootFrame() instanceof Frame shared) {
            shared.setIconImages(Icons.appIcons());
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
