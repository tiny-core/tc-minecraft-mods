package org.tinycore.cloud.item.policy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Política de itens de uma nuvem: o modo (lista negra ou branca) e as regras do dono. É a regra 2 do
 * {@code TransferGuard} (plano §6) e vale na entrada E na saída, porque o dono pode mudar a política depois
 * de o item já estar na nuvem.
 *
 * <p>Precedência: a regra mais <b>específica</b> vence (item &gt; tag &gt; mod &gt; modo). No mesmo nível,
 * {@code BLOCK} vence {@code ALLOW}: na dúvida, bloquear. Assim o dono pode, por exemplo, bloquear um mod
 * inteiro e liberar um item dele.
 *
 * @param version versão da política no TCMine; o servidor busca a nova quando o heartbeat avisa que mudou
 */
public record ItemPolicy(@NotNull Mode mode, @NotNull List<ItemRule> rules, long version) {

    public enum Mode {
        /** Tudo entra, menos o que as regras bloqueiam (padrão). */
        BLOCKLIST,
        /** Nada entra, menos o que as regras permitem ("só vanilla + mods aprovados"). */
        ALLOWLIST
    }

    /** Sem regras, lista negra: tudo passa. */
    public static final ItemPolicy OPEN = new ItemPolicy(Mode.BLOCKLIST, List.of(), 0);

    public ItemPolicy {
        rules = List.copyOf(rules);
    }

    /**
     * @param itemId id do item ({@code mod:item})
     * @param tags   tags do item, sem {@code #} ({@code c:ingots}...)
     */
    public @NotNull Decision evaluate(@NotNull String itemId, @NotNull Set<String> tags) {
        String id = itemId.toLowerCase(Locale.ROOT);
        String modId = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";

        Decision byItem = match(ItemRule.Scope.ITEM, id::equals);
        if (byItem != null) return byItem;
        Decision byTag = match(ItemRule.Scope.TAG, tags::contains);
        if (byTag != null) return byTag;
        Decision byMod = match(ItemRule.Scope.MOD, modId::equals);
        if (byMod != null) return byMod;
        return new Decision(mode == Mode.BLOCKLIST, null);
    }

    private @Nullable Decision match(ItemRule.Scope scope, java.util.function.Predicate<String> matches) {
        ItemRule allow = null;
        for (ItemRule rule : rules) {
            if (rule.scope() != scope || !matches.test(rule.pattern())) continue;
            if (rule.action() == ItemRule.Action.BLOCK) return new Decision(false, rule);
            allow = rule;
        }
        return allow == null ? null : new Decision(true, allow);
    }

    /**
     * @param allowed se o item pode entrar/sair
     * @param rule    regra que decidiu; {@code null} quando foi o modo
     */
    public record Decision(boolean allowed, @Nullable ItemRule rule) {}
}
