package com.postit;

import java.awt.BorderLayout;
import java.awt.Color;

import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingWorker;

/**
 * Janela de Configuracoes. Hoje tem um ajuste de verdade -- o inicio automatico com o
 * sistema -- mais o caminho onde as notas ficam.
 */
public final class SettingsDialog extends JDialog {

    private static final int TEXT_WIDTH = 340;

    private final NoteStore store;
    private final JCheckBox autostartBox = new JCheckBox("Iniciar o postit com o Windows");
    private final JLabel autostartHint = new JLabel();
    private final JButton closeButton = new JButton("Fechar");

    public SettingsDialog(Window owner, NoteStore store) {
        super(owner, "Configuracoes do postit", ModalityType.APPLICATION_MODAL);
        this.store = store;

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
        content.add(buildAutostartSection());
        content.add(Box.createVerticalStrut(16));
        content.add(new JSeparator());
        content.add(Box.createVerticalStrut(12));
        content.add(buildStorageSection());

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        add(buildButtonBar(), BorderLayout.SOUTH);
        getRootPane().setDefaultButton(closeButton);

        refreshAutostart();
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildAutostartSection() {
        JPanel panel = column();
        panel.add(leftAligned(sectionTitle("Inicializacao")));

        autostartBox.setAlignmentX(LEFT_ALIGNMENT);
        autostartBox.addActionListener(e -> applyAutostart(autostartBox.isSelected()));
        panel.add(leftAligned(autostartBox));

        autostartHint.setFont(autostartHint.getFont().deriveFont(Font.PLAIN, 11f));
        autostartHint.setForeground(new Color(0x5A5A5A));
        autostartHint.setAlignmentX(LEFT_ALIGNMENT); // sem leftAligned: o texto quebrado precisa crescer
        panel.add(autostartHint);
        return panel;
    }

    private JPanel buildStorageSection() {
        JPanel panel = column();
        panel.add(leftAligned(sectionTitle("Notas")));

        JLabel path = new JLabel(store.notesDir().toString());
        path.setFont(path.getFont().deriveFont(Font.PLAIN, 11f));
        path.setForeground(new Color(0x5A5A5A));
        panel.add(leftAligned(path));
        panel.add(Box.createVerticalStrut(6));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        JButton openNotes = new JButton("Abrir a pasta das notas");
        openNotes.addActionListener(e -> openFolder(store.notesDir()));
        buttons.add(openNotes);
        buttons.add(Box.createHorizontalStrut(8));

        JButton openTrash = new JButton("Abrir a lixeira");
        boolean hasTrash = store.trashHasNotes();
        openTrash.setEnabled(hasTrash);
        openTrash.setToolTipText(hasTrash
                ? "Para restaurar, mova o arquivo da nota de volta para a pasta das notas"
                : "A lixeira esta vazia");
        openTrash.addActionListener(e -> openFolder(store.trashDir()));
        buttons.add(openTrash);

        panel.add(leftAligned(buttons));
        return panel;
    }

    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        closeButton.addActionListener(e -> dispose());
        bar.add(closeButton);
        return bar;
    }

    // ------------------------------------------------------------------ acoes

    /**
     * Ligar depende de um processo externo (PowerShell, para escrever o .lnk), o que leva
     * algumas centenas de milissegundos -- fora da EDT, para o dialogo nao congelar.
     */
    private void applyAutostart(boolean enabled) {
        autostartBox.setEnabled(false);
        autostartHint.setText(wrap(enabled ? "Ligando..." : "Desligando..."));

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Autostart.setEnabled(enabled);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    JOptionPane.showMessageDialog(SettingsDialog.this,
                            "Nao foi possivel " + (enabled ? "ligar" : "desligar")
                                    + " o inicio automatico.\n\n" + cause.getMessage(),
                            "postit", JOptionPane.ERROR_MESSAGE);
                }
                refreshAutostart(); // reflete o estado real, mesmo depois de falhar
            }
        }.execute();
    }

    /** Le o estado de verdade do sistema e redesenha a secao. */
    private void refreshAutostart() {
        boolean available = Autostart.status() == Autostart.Status.AVAILABLE;
        autostartBox.setEnabled(available);
        autostartBox.setSelected(Autostart.isEnabled());
        autostartHint.setText(wrap(Autostart.statusExplanation()));
        if (isShowing()) {
            pack();
        }
    }

    private void openFolder(Path folder) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            JOptionPane.showMessageDialog(this, folder.toString(),
                    "postit", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(this,
                    "Nao foi possivel abrir a pasta:\n" + folder + "\n\n" + e.getMessage(),
                    "postit", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ layout

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        return label;
    }

    private static <T extends javax.swing.JComponent> T leftAligned(T component) {
        component.setAlignmentX(LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                component.getPreferredSize().height));
        return component;
    }

    /** JLabel nao quebra linha sozinho; com HTML e largura fixa, quebra. */
    private static String wrap(String text) {
        return "<html><body style='width:" + TEXT_WIDTH + "px'>" + text + "</body></html>";
    }
}
