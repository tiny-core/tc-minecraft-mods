package org.tinycore.colonybridge.client.ui;

/**
 * Tokens de cor da interface do mod (paleta do TCMine), no formato ARGB que o {@code GuiGraphics} usa:
 * {@code 0xAARRGGBB}. Centralizar aqui mantém telas e, no futuro, os monitores com a mesma identidade.
 */
public final class UiColors {

    public static final int BACKGROUND = 0xF01B1E26;
    public static final int PANEL = 0xFF242832;
    public static final int PANEL_HOVER = 0xFF2E3340;
    public static final int BORDER = 0xFF3A3F4B;
    public static final int ACCENT = 0xFFF97316;   // laranja TCMine
    public static final int HIGHLIGHT = 0xFF22B8E8; // ciano TCMine

    public static final int TEXT = 0xFFE6E8EC;
    public static final int TEXT_MUTED = 0xFFA3A8B3;

    public static final int SUCCESS = 0xFF4ADE80;
    public static final int WARNING = 0xFFFBBF24;
    public static final int DANGER = 0xFFF87171;

    private UiColors() {}
}
