package com.recados;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Diario de bordo do Recados: registra o que foi teclado e clicado, o que cada comando fez
 * com o texto e -- quando a tela congela -- de onde a EDT nao esta saindo.
 *
 * Fica desligado por padrao. Liga com -Drecados.trace (grava em ~/.recados/trace/) ou
 * -Drecados.trace=C:\algum\arquivo.log. Desligado, todo metodo daqui e um "if" e volta:
 * o build normal nao paga nada por esta classe existir.
 *
 * Por que um vigia em vez de um profiler: o travamento que perseguimos come a propria EDT,
 * entao qualquer coisa que dependa dela -- inclusive um dump pedido pela interface --
 * congela junto. O vigia mora numa thread propria, so pergunta "voce ainda responde?" e,
 * quando a resposta demora, fotografa a pilha de todas as threads. Varias vezes, porque uma
 * foto so nao distingue "esta lento" de "esta em circulo".
 */
public final class Trace {

    /** Silencio total quando desligado -- e o caso do build que vai para o usuario. */
    private static final boolean ON = System.getProperty("recados.trace") != null;

    /** Depois deste tempo sem resposta a EDT e considerada travada. */
    private static final long TRAVOU_MS = 2_000;

    /** Intervalo entre as perguntas "voce ainda responde?". */
    private static final long BATIDA_MS = 250;

    /** Enquanto continuar travada, uma foto nova da pilha a cada tanto. */
    private static final long FOTO_MS = 3_000;

    private static final DateTimeFormatter RELOGIO = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int TRECHO_MAX = 1_500;
    private static final int QUADROS_MAX = 40;

    private static PrintWriter saida;
    private static Path arquivo;
    private static final AtomicLong ultimaResposta = new AtomicLong(System.currentTimeMillis());
    private static volatile String ultimoComando = "(nenhum)";
    private static volatile String ultimoHtml = "(nada ainda)";

    private Trace() {
    }

    public static boolean ligado() {
        return ON;
    }

    public static Path arquivo() {
        return arquivo;
    }

    /** Abre o arquivo, escuta teclado e mouse e solta o vigia. Chamar uma vez, no inicio. */
    public static synchronized void instalar() {
        if (!ON || saida != null) {
            return;
        }
        try {
            arquivo = destino();
            Files.createDirectories(arquivo.getParent());
            Writer w = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            saida = new PrintWriter(w, true);
        } catch (IOException e) {
            System.err.println("Trace: nao consegui abrir " + arquivo + ": " + e);
            return;
        }
        linha("=== Recados iniciou | pid " + ProcessHandle.current().pid()
                + " | java " + System.getProperty("java.version")
                + " | " + System.getProperty("os.name"));
        System.out.println("Trace ligado: " + arquivo);
        escutarEntrada();
        soltarVigia();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> linha("=== Recados encerrou"), "trace-fim"));
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            linha("!! excecao em " + t.getName() + ": " + e);
            for (StackTraceElement s : e.getStackTrace()) {
                linha("!!     " + s);
            }
        });
    }

    private static Path destino() {
        String valor = System.getProperty("recados.trace", "");
        if (!valor.isBlank() && !"true".equalsIgnoreCase(valor)) {
            return Path.of(valor).toAbsolutePath();
        }
        String nome = "trace-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + "-" + ProcessHandle.current().pid() + ".log";
        return Path.of(System.getProperty("user.home"), ".recados", "trace", nome);
    }

    // ------------------------------------------------------------------ registro

    public static void linha(String texto) {
        if (!ON || saida == null) {
            return;
        }
        synchronized (Trace.class) {
            saida.println(LocalDateTime.now().format(RELOGIO)
                    + " [" + Thread.currentThread().getName() + "] " + texto);
        }
    }

    /**
     * Envolve um comando do editor: sai no diario o que entrou, quanto demorou e como o
     * documento ficou depois. E o par "apertei isto / o texto virou aquilo" que permite
     * repetir o caso passo a passo mais tarde.
     */
    public static void comando(String nome, String antes, Runnable acao, Supplier<String> depois) {
        if (!ON) {
            acao.run();
            return;
        }
        ultimoComando = nome + "  " + antes;
        linha("> " + nome + "  " + antes);
        long inicio = System.nanoTime();
        try {
            acao.run();
        } catch (RuntimeException | Error e) {
            linha("< " + nome + " ESTOUROU: " + e);
            throw e;
        } finally {
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            String html;
            try {
                html = depois == null ? "" : trecho(depois.get());
            } catch (RuntimeException e) {
                html = "(nao consegui ler o html: " + e + ")";
            }
            ultimoHtml = html;
            linha("< " + nome + " em " + ms + "ms  html=" + html);
            ultimoComando = "(nenhum)";
        }
    }

    private static String trecho(String html) {
        if (html == null) {
            return "(nulo)";
        }
        String uma = html.replace("\r", "").replace("\n", "\\n");
        return uma.length() <= TRECHO_MAX
                ? uma
                : uma.substring(0, TRECHO_MAX) + "...(+" + (uma.length() - TRECHO_MAX) + ")";
    }

    // ------------------------------------------------------------------ teclado e mouse

    private static void escutarEntrada() {
        Toolkit.getDefaultToolkit().addAWTEventListener(evento -> {
            if (evento instanceof KeyEvent tecla && tecla.getID() == KeyEvent.KEY_PRESSED) {
                linha("TECLA " + descreve(tecla) + "  em " + alvo(tecla.getComponent()));
            } else if (evento instanceof MouseEvent rato && rato.getID() == MouseEvent.MOUSE_PRESSED) {
                linha("MOUSE botao" + rato.getButton()
                        + (rato.getClickCount() > 1 ? " x" + rato.getClickCount() : "")
                        + " em " + alvo(rato.getComponent())
                        + " (" + rato.getX() + "," + rato.getY() + ")");
            }
        }, AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
    }

    private static String descreve(KeyEvent tecla) {
        String mods = InputEvent.getModifiersExText(tecla.getModifiersEx());
        String nome = KeyEvent.getKeyText(tecla.getKeyCode());
        return mods.isEmpty() ? nome : mods + "+" + nome;
    }

    /**
     * O nome da classe basta para saber onde a tecla caiu; a dica do botao diz qual icone da
     * barra foi apertado -- que e exatamente o que queremos repetir depois.
     */
    private static String alvo(Component c) {
        if (c == null) {
            return "?";
        }
        String nome = c.getClass().getSimpleName();
        if (c instanceof javax.swing.JComponent j && j.getToolTipText() != null) {
            nome += "[" + j.getToolTipText() + "]";
        }
        return nome;
    }

    // ------------------------------------------------------------------ vigia da EDT

    private static void soltarVigia() {
        Thread vigia = new Thread(() -> {
            long ultimaFoto = 0;
            while (true) {
                long agora = System.currentTimeMillis();
                SwingUtilities.invokeLater(() -> ultimaResposta.set(System.currentTimeMillis()));
                long parada = agora - ultimaResposta.get();
                if (parada >= TRAVOU_MS && agora - ultimaFoto >= FOTO_MS) {
                    ultimaFoto = agora;
                    fotografar(parada);
                }
                try {
                    Thread.sleep(BATIDA_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "recados-vigia");
        vigia.setDaemon(true);
        vigia.setPriority(Thread.MAX_PRIORITY);
        vigia.start();
    }

    /** Fotografa a pilha das threads que importam e o consumo de memoria. */
    private static void fotografar(long parada) {
        Runtime rt = Runtime.getRuntime();
        long usados = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        linha("!! EDT sem responder ha " + parada + "ms | memoria " + usados + "MB de "
                + rt.maxMemory() / (1024 * 1024) + "MB | ultimo comando: " + ultimoComando);
        linha("!! html de antes: " + ultimoHtml);
        Thread.getAllStackTraces().forEach((t, pilha) -> {
            if (pilha.length == 0 || "recados-vigia".equals(t.getName())) {
                return;
            }
            boolean interessa = t.getName().startsWith("AWT-EventQueue")
                    || t.getState() == Thread.State.RUNNABLE;
            if (!interessa) {
                return;
            }
            linha("!!   thread \"" + t.getName() + "\" " + t.getState());
            int limite = Math.min(pilha.length, QUADROS_MAX);
            for (int i = 0; i < limite; i++) {
                linha("!!     " + pilha[i]);
            }
            if (pilha.length > limite) {
                linha("!!     ... (+" + (pilha.length - limite) + " quadros)");
            }
        });
    }
}
