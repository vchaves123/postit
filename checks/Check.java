/**
 * Placar das checagens. Sem framework de teste de proposito: o Kaspersky desta maquina
 * encerra o booter do maven-surefire como PDM:Trojan.Win32.Generic, entao "mvn test" nunca
 * chega a rodar aqui. Estas checagens sao classes normais, compiladas contra o jar.
 */
public final class Check {

    private static int falhas;
    private static int total;

    private Check() {
    }

    public static void grupo(String nome) {
        System.out.println();
        System.out.println("== " + nome + " ==");
    }

    public static void that(String label, boolean ok) {
        total++;
        if (!ok) {
            falhas++;
        }
        System.out.println((ok ? "  ok    " : "  FALHA ") + label);
    }

    public static int falhas() {
        return falhas;
    }

    public static int total() {
        return total;
    }
}
