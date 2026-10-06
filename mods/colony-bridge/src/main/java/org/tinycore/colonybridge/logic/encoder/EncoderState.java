package org.tinycore.colonybridge.logic.encoder;

/**
 * Situação de uma linha do TC Pattern Encoder (um item pedido pela colônia sem padrão no AE2). A ordem é a da tela:
 * o que dá para codificar primeiro. O ordinal vai no pacote para o cliente.
 */
public enum EncoderState {
    /** Receita de bancada encontrada: pronto para virar padrão. */
    READY,
    /** O padrão já está nos slots de saída (falta levá-lo ao Pattern Provider). */
    IN_OUTPUT,
    /** Nenhuma receita de bancada produz exatamente este item (fornalha, máquina, item com componentes...). */
    NO_RECIPE,
    /** Item que o mod não codifica (Domum Ornamentum / bancada do arquiteto). */
    EXCLUDED;

    private static final EncoderState[] VALUES = values();

    public static EncoderState byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NO_RECIPE;
    }

    public String translationKey() {
        return "gui.tccolonybridge.encoder.state." + name().toLowerCase();
    }
}
