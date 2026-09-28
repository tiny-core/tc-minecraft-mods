package org.tinycore.colonybridge.logic.crafting;

import java.util.List;
import java.util.Set;

/**
 * Regras já resolvidas para escolher o item a craftar num pedido por tag: o que a ponte configurou,
 * completado com a config do servidor onde a ponte deixou "padrão do servidor".
 * <p>
 * Existe para o {@link CraftCandidates} não saber de onde vêm as regras (ponte, config, futuro
 * abastecedor): ele só recebe este objeto pronto. Montado por {@code ColonyBridgeBlockEntity.craftRules()}.
 *
 * @param preference   ordem dos candidatos
 * @param preferredIds ids de itens em ordem de preferência (modo {@link CraftPreference#LIST})
 * @param modMode      como os mods marcados são usados
 * @param mods         mods marcados (namespaces, ex.: "minecraft", "mekanism")
 * @param vanillaOnly  regra do servidor: só itens do Minecraft, independente da ponte
 */
public record CraftRules(CraftPreference preference, List<String> preferredIds, ModFilterMode modMode,
                         Set<String> mods, boolean vanillaOnly) {}
