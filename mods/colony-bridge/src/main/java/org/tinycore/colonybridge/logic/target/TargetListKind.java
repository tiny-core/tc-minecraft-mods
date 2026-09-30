package org.tinycore.colonybridge.logic.target;

/**
 * Qual lista uma linha pertence e, por isso, quais regras valem para ela (regra pura, testada):
 * <table>
 *   <tr><th></th><th>item</th><th>tag</th><th>mod</th><th>quantidade</th><th>"tudo"</th></tr>
 *   <tr><td>{@link #KEEP}</td><td>sim</td><td>sim</td><td>não</td><td>sim</td><td>não</td></tr>
 *   <tr><td>{@link #SURPLUS}</td><td>sim</td><td>sim</td><td>sim</td><td>sim</td><td>sim</td></tr>
 *   <tr><td>{@link #FILTER}</td><td>sim</td><td>sim</td><td>sim</td><td>não</td><td>não</td></tr>
 * </table>
 * "Tudo" em "manter" esvaziaria a rede ME dentro do armazém; "mod" em "manter" não diz o que trazer.
 * <p>
 * O {@link #name()} é salvo no NBT da lista: não renomear.
 */
public enum TargetListKind {
    /** Abastecedor, "Manter no armazém": mínimo que deve haver no armazém (vem da rede ME). */
    KEEP(false, true, false),
    /** Abastecedor, "Excedente para o ME": o que passar do limite volta para a rede. */
    SURPLUS(true, true, true),
    /** Filtro da Ponte (permitir/bloquear). */
    FILTER(true, false, false);

    /** Quantidade máxima de uma linha (a soma de uma tag pode passar de uma pilha com folga). */
    public static final int MAX_AMOUNT = 99_999;
    /** Quantidade ao adicionar uma linha nova. */
    public static final int DEFAULT_AMOUNT = 64;

    private final boolean allowsMod;
    private final boolean hasAmount;
    private final boolean allowsAll;

    TargetListKind(boolean allowsMod, boolean hasAmount, boolean allowsAll) {
        this.allowsMod = allowsMod;
        this.hasAmount = hasAmount;
        this.allowsAll = allowsAll;
    }

    /** true se esta lista aceita linhas do tipo. */
    public boolean allows(TargetKind kind) {
        return kind != TargetKind.MOD || allowsMod;
    }

    /** true se as linhas têm quantidade (meta ou limite). */
    public boolean hasAmount() {
        return hasAmount;
    }

    /** true se a linha pode usar "tudo" no lugar da quantidade. */
    public boolean allowsAll() {
        return allowsAll;
    }

    /** Quantidade limitada ao que a lista aceita (o valor vem da tela, então nunca é confiado). */
    public int clampAmount(int amount) {
        return hasAmount ? Math.max(0, Math.min(MAX_AMOUNT, amount)) : 0;
    }

    /** Lê o nome salvo; desconhecido → {@code fallback}. */
    public static TargetListKind byName(String name, TargetListKind fallback) {
        for (TargetListKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return fallback;
    }
}
