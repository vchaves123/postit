/**
 * Roda todas as checagens do Recados. Cada grupo usa um diretorio temporario proprio, entao
 * nada aqui toca as suas notas em ~/.recados.
 *
 * <p>Uso: {@code run-checks.ps1} (ou {@code run-checks.sh}). Sai com codigo 1 se algo falhar.
 */
public final class Checks {

    public static void main(String[] args) throws Exception {
        StoreChecks.run();
        WindowChecks.run();
        HtmlChecks.run();

        System.out.println();
        if (Check.falhas() == 0) {
            System.out.println("TODAS AS " + Check.total() + " CHECAGENS PASSARAM");
        } else {
            System.out.println(Check.falhas() + " DE " + Check.total() + " FALHARAM");
        }
        System.exit(Check.falhas() == 0 ? 0 : 1);
    }
}
