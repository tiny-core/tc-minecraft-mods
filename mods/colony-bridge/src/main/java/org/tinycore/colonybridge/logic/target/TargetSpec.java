package org.tinycore.colonybridge.logic.target;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * O alvo de uma linha, já lido do texto da caixa: tipo ({@link TargetKind}) + id normalizado.
 * <ul>
 *   <li>{@code minecraft:iron_ingot} ou só {@code iron_ingot} → item ({@code minecraft} é o padrão);</li>
 *   <li>{@code #c:ingots/iron} → tag;</li>
 *   <li>{@code @mekanism} → mod.</li>
 * </ul>
 * Aqui só se confere a <b>sintaxe</b> (regra pura, testada sem o jogo). Se o item, a tag ou o mod existem no
 * modpack é conferido no servidor, com os registros carregados. O texto vem do cliente, então tem tamanho
 * máximo e nunca é confiado.
 * <p>
 * {@code record} em Java ≈ {@code record} em C#: classe imutável de dados com igualdade por valor.
 *
 * @param id id sem prefixo: {@code namespace:caminho} para item e tag, só o id do mod para mod
 */
public record TargetSpec(TargetKind kind, String id) {

    /** Tamanho máximo do texto aceito (protege pacotes e NBT contra textos gigantes). */
    public static final int MAX_TEXT = 128;

    /** Regra de id de mod do NeoForge: minúsculas, começa com letra, 2 a 64 caracteres. */
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    /**
     * Lê o texto digitado. Espaços nas pontas e maiúsculas são ignorados.
     *
     * @return null se o texto está vazio, é longo demais ou a sintaxe é inválida
     */
    public static @Nullable TargetSpec parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String clean = text.trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty() || clean.length() > MAX_TEXT) {
            return null;
        }
        if (clean.startsWith(TargetKind.MOD.prefix())) {
            String mod = clean.substring(1);
            return MOD_ID.matcher(mod).matches() ? new TargetSpec(TargetKind.MOD, mod) : null;
        }
        if (clean.startsWith(TargetKind.TAG.prefix())) {
            ResourceLocation tag = location(clean.substring(1));
            return tag == null ? null : new TargetSpec(TargetKind.TAG, tag.toString());
        }
        ResourceLocation item = location(clean);
        return item == null ? null : new TargetSpec(TargetKind.ITEM, item.toString());
    }

    /** Alvo de item a partir do id do registro (ex.: item solto no ícone da linha). */
    public static TargetSpec ofItem(ResourceLocation itemId) {
        return new TargetSpec(TargetKind.ITEM, itemId.toString());
    }

    /** Texto para a caixa, com o prefixo do tipo. {@code parse(text())} devolve o mesmo alvo. */
    public String text() {
        return kind.prefix() + id;
    }

    /** Id como {@code ResourceLocation} (item e tag); null para mod. */
    public @Nullable ResourceLocation location() {
        return kind == TargetKind.MOD ? null : ResourceLocation.tryParse(id);
    }

    /**
     * {@code ResourceLocation.tryParse} devolve null para caracteres inválidos; o caminho vazio
     * ({@code "minecraft:"}) também é recusado, porque não aponta para nada.
     */
    private static @Nullable ResourceLocation location(String text) {
        ResourceLocation location = ResourceLocation.tryParse(text);
        return location == null || location.getPath().isEmpty() ? null : location;
    }
}
