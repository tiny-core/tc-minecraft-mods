package org.tinycore.core.client.ui;

/** Formatação de números para espaços pequenos (cartões da tela, monitores). */
public final class UiFormat {

    private UiFormat() {}

    /** 1234 → "1.2k", 3400000 → "3.4M" (separador decimal segue o idioma do sistema). */
    public static String compact(long value) {
        if (value < 1_000) {
            return Long.toString(value);
        }
        if (value < 1_000_000) {
            return String.format("%.1fk", value / 1_000.0);
        }
        return String.format("%.1fM", value / 1_000_000.0);
    }
}
