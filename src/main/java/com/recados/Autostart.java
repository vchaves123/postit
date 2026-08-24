package com.recados;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.CodeSource;
import java.util.Locale;
import java.util.Optional;

/**
 * Liga e desliga o inicio automatico com o sistema, sem depender de script externo.
 *
 * <p>O mecanismo e um atalho {@code recados.lnk} na pasta Inicializar do usuario -- o mesmo
 * lugar que o Windows oferece em "Aplicativos de inicializacao", e o mesmo arquivo que o
 * {@code install-startup.ps1} cria, entao os dois caminhos nunca brigam.
 *
 * <p>Nao usamos a chave {@code CurrentVersion\Run}: gravar um caminho de executavel entre
 * aspas ali, a partir de um processo Java, e barrado pela protecao do Windows (o
 * {@code CreateProcess} do reg.exe volta com "Access is denied"). O atalho passa limpo.
 *
 * <p>Consultar e desligar sao I/O puro -- e o estado mostrado na tela e o do disco, nao um
 * palpite guardado. So ligar precisa do PowerShell, porque criar um {@code .lnk} depende do
 * WScript.Shell.
 */
public final class Autostart {

    /** Se e possivel configurar o inicio automatico aqui, e por que nao, quando nao for. */
    public enum Status {
        /** Windows, rodando pelo exe do jpackage ou pelo jar: pode ligar e desligar. */
        AVAILABLE,
        /** Rodando a partir das classes compiladas (IDE): nao ha o que apontar. */
        NOT_PACKAGED,
        /** Fora do Windows: nao implementado nesta versao. */
        UNSUPPORTED_OS
    }

    /** Falha ao mexer no atalho; a mensagem e para o usuario ler. */
    public static final class AutostartException extends Exception {
        public AutostartException(String message) {
            super(message);
        }
    }

    private static final String LINK_NAME = "recados.lnk";

    private Autostart() {
    }

    public static Status status() {
        if (!isWindows()) {
            return Status.UNSUPPORTED_OS;
        }
        return nativeLauncher().isPresent() || jarPath().isPresent()
                ? Status.AVAILABLE : Status.NOT_PACKAGED;
    }

    /**
     * O exe do jpackage, quando o Recados esta rodando por ele. O atalho tem que apontar para
     * este executavel, e nao para javaw: e dele que a barra de tarefas tira o icone.
     */
    private static Optional<Path> nativeLauncher() {
        return ProcessHandle.current().info().command()
                .map(Path::of)
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".exe") && !name.startsWith("java")
                            && Files.isRegularFile(path);
                });
    }

    /** Explicacao curta para mostrar embaixo da caixa de selecao. */
    public static String statusExplanation() {
        return switch (status()) {
            case AVAILABLE -> "Cria um atalho na pasta Inicializar do Windows apontando para o "
                    + "executavel atual. Se voce mover a pasta do Recados, desmarque e marque "
                    + "de novo.";
            case NOT_PACKAGED -> "Disponivel quando o Recados roda pelo jar. Rode \"mvn package\" e "
                    + "inicie por target/recados.jar.";
            case UNSUPPORTED_OS -> "Nesta versao o inicio automatico so funciona no Windows.";
        };
    }

    /** O estado de verdade: o atalho esta la ou nao. */
    public static boolean isEnabled() {
        return isWindows() && Files.isRegularFile(linkPath());
    }

    public static Path linkPath() {
        return startupDir().resolve(LINK_NAME);
    }

    /**
     * Aplica a mudanca e confere no disco -- se nao pegou, o usuario fica sabendo agora
     * em vez de descobrir no proximo login.
     */
    public static void setEnabled(boolean enabled) throws AutostartException {
        if (status() != Status.AVAILABLE) {
            throw new AutostartException(statusExplanation());
        }
        if (enabled) {
            createShortcut();
            if (!isEnabled()) {
                throw new AutostartException("O atalho nao apareceu em " + startupDir() + ".");
            }
        } else {
            try {
                Files.deleteIfExists(linkPath());
            } catch (IOException e) {
                throw new AutostartException("Nao foi possivel remover " + linkPath()
                        + ": " + e.getMessage());
            }
            if (isEnabled()) {
                throw new AutostartException("O atalho continua em " + startupDir() + ".");
            }
        }
    }

    /** Cria (ou reaponta) o atalho via WScript.Shell, que e o unico jeito de escrever um .lnk. */
    private static void createShortcut() throws AutostartException {
        Path target;
        String arguments;
        Path workingDir;
        Optional<Path> exe = nativeLauncher();
        if (exe.isPresent()) {
            target = exe.get();
            arguments = "";
            workingDir = exe.get().getParent();
        } else {
            Path jar = jarPath().orElseThrow(() -> new AutostartException(statusExplanation()));
            target = launcher();
            arguments = "-jar \"" + jar + "\"";
            workingDir = jar.getParent();
        }
        String script = "$s = (New-Object -ComObject WScript.Shell).CreateShortcut(" + ps(linkPath()) + ");"
                + "$s.TargetPath = " + ps(target) + ";"
                + "$s.Arguments = " + ps(arguments) + ";"
                + "$s.WorkingDirectory = " + ps(workingDir) + ";"
                + "$s.Description = 'Recados - notas na area de trabalho';"
                + "$s.IconLocation = " + ps(target + ",0") + ";"
                + "$s.Save()";

        // -EncodedCommand em vez de -Command: o script vai em base64, entao as aspas duplas
        // que cercam o caminho do jar nao passam pela linha de comando do Windows, que as
        // comeria (e o inicio automatico quebraria em caminhos com espaco).
        String encoded = Base64.getEncoder()
                .encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        try {
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), consoleCharset()).strip();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new AutostartException("O PowerShell falhou (codigo " + exitCode + "): "
                        + firstLine(output));
            }
        } catch (IOException e) {
            throw new AutostartException("Nao foi possivel executar o PowerShell: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AutostartException("Interrompido antes de criar o atalho.");
        }
    }

    // ------------------------------------------------------------------ apoio

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static Path startupDir() {
        String appData = System.getenv("APPDATA");
        Path base = appData != null && !appData.isBlank()
                ? Path.of(appData)
                : Path.of(System.getProperty("user.home"), "AppData", "Roaming");
        return base.resolve(Path.of("Microsoft", "Windows", "Start Menu", "Programs", "Startup"));
    }

    /** O javaw do JDK em uso: mesma versao que esta rodando, e sem janela de console. */
    private static Path launcher() {
        Path bin = Path.of(System.getProperty("java.home")).resolve("bin");
        Path javaw = bin.resolve("javaw.exe");
        return Files.isRegularFile(javaw) ? javaw : bin.resolve("java.exe");
    }

    /** O jar de onde esta classe foi carregada, quando ela veio de um jar. */
    private static Optional<Path> jarPath() {
        CodeSource source = RecadosApp.class.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(source.getLocation().toURI());
            boolean isJar = path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar");
            return isJar && Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Literal de string do PowerShell: aspas simples, dobrando as que existirem dentro. */
    private static String ps(Object value) {
        return "'" + value.toString().replace("'", "''") + "'";
    }

    private static String firstLine(String text) {
        return text.lines().filter(line -> !line.isBlank()).findFirst().orElse("(sem saida)");
    }

    /** A saida do PowerShell vem na codificacao nativa do console, nao em UTF-8. */
    private static Charset consoleCharset() {
        String name = System.getProperty("native.encoding");
        if (name != null) {
            try {
                return Charset.forName(name);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException ignored) {
                // cai no padrao
            }
        }
        return Charset.defaultCharset();
    }
}
