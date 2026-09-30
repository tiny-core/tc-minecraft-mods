package org.tinycore.colonybridge.logic.target;

/**
 * O que uma linha de lista (Abastecedor ou filtro da Ponte) aponta: um item, uma tag de itens ou todos os
 * itens de um mod. O prefixo é o que o jogador digita na caixa de texto, igual à busca do Terminal.
 * <p>
 * O nome do valor não é salvo (a linha guarda o texto, ex.: {@code #c:ingots/iron}), então a ordem pode mudar.
 */
public enum TargetKind {
    /** {@code minecraft:iron_ingot} */
    ITEM(""),
    /** {@code #c:ingots/iron} */
    TAG("#"),
    /** {@code @mekanism} */
    MOD("@");

    private final String prefix;

    TargetKind(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
