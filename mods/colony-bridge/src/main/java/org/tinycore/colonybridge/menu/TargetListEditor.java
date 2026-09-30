package org.tinycore.colonybridge.menu;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.logic.target.TargetLine;
import org.tinycore.colonybridge.logic.target.TargetList;
import org.tinycore.colonybridge.logic.target.TargetListHost;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.logic.target.TargetResolver;
import org.tinycore.colonybridge.logic.target.TargetSpec;
import org.tinycore.colonybridge.network.TargetEditPayload;
import org.tinycore.colonybridge.network.TargetEditPayload.Op;

/**
 * Aplica no servidor uma edição de lista vinda da tela ({@link TargetEditPayload}), para qualquer bloco com
 * listas ({@link TargetListHost}). Quem chama já validou o menu, a distância e a permissão
 * ({@code ModNetwork.validMenu}); aqui se valida o <b>conteúdo</b>:
 * <ul>
 *   <li>a lista existe nesse bloco e aceita o tipo de alvo ({@link TargetLine#sanitized});</li>
 *   <li>o texto é um alvo válido e <b>existe</b> no modpack ({@link TargetResolver#exists});</li>
 *   <li>índice dentro da lista e lista abaixo do limite da config ({@link TargetList}).</li>
 * </ul>
 * Item do cursor: sempre o do próprio jogador no servidor, copiado com 1 unidade (nada sai da mão dele).
 */
public final class TargetListEditor {

    private TargetListEditor() {}

    /**
     * @param carried item no cursor do jogador (lido no servidor)
     * @return true se a lista mudou
     */
    public static boolean apply(TargetListHost host, TargetEditPayload edit, ItemStack carried) {
        TargetListKind kind = kindById(edit.kind());
        Op op = Op.byId(edit.op());
        TargetList list = kind == null ? null : host.targetList(kind);
        if (list == null || op == null) {
            return false;
        }
        boolean changed = switch (op) {
            case ADD_TEXT -> {
                TargetSpec spec = validSpec(edit.text());
                yield spec != null && list.add(TargetLine.ofSpec(spec, defaultAmount(kind), false), maxLines());
            }
            case ADD_CURSOR -> addStack(list, carried);
            case ADD_STACK -> addStack(list, edit.stack());
            case SET_TEXT -> {
                TargetSpec spec = validSpec(edit.text());
                TargetLine old = lineAt(list, edit.index());
                yield spec != null && old != null
                        && list.set(edit.index(), TargetLine.ofSpec(spec, old.amount(), old.all()));
            }
            case SET_CURSOR -> setStack(list, edit.index(), carried);
            case SET_STACK -> setStack(list, edit.index(), edit.stack());
            case SET_AMOUNT -> {
                TargetLine old = lineAt(list, edit.index());
                yield old != null && list.set(edit.index(),
                        new TargetLine(old.spec(), old.item(), edit.amount(), edit.all()));
            }
            case REMOVE -> list.remove(edit.index());
            case SELECT -> false;
        };
        if (changed) {
            host.onTargetListChanged(kind);
        }
        return changed;
    }

    /** Linha nova a partir de um item (cursor, JEI ou shift-clique no inventário). */
    public static boolean addStack(TargetList list, ItemStack stack) {
        return !stack.isEmpty() && list.add(TargetLine.ofStack(stack, defaultAmount(list.kind())), maxLines());
    }

    /** Troca o alvo da linha pelo item, mantendo a quantidade. Mão vazia não apaga (o "✕" é para isso). */
    private static boolean setStack(TargetList list, int index, ItemStack stack) {
        TargetLine old = lineAt(list, index);
        if (old == null || stack.isEmpty()) {
            return false;
        }
        TargetLine line = TargetLine.ofStack(stack, old.amount());
        return list.set(index, new TargetLine(line.spec(), line.item(), old.amount(), old.all()));
    }

    private static @Nullable TargetSpec validSpec(String text) {
        TargetSpec spec = TargetSpec.parse(text);
        if (spec == null || !TargetResolver.exists(spec)) {
            ColonyBridgeMod.LOG.debug("Alvo de lista recusado: '{}'", text);
            return null;
        }
        return spec;
    }

    private static @Nullable TargetLine lineAt(TargetList list, int index) {
        return index >= 0 && index < list.size() ? list.lines().get(index) : null;
    }

    private static int defaultAmount(TargetListKind kind) {
        return kind.hasAmount() ? TargetListKind.DEFAULT_AMOUNT : 0;
    }

    private static int maxLines() {
        return Config.LIST_MAX_LINES.get();
    }

    private static @Nullable TargetListKind kindById(int id) {
        TargetListKind[] kinds = TargetListKind.values();
        return id >= 0 && id < kinds.length ? kinds[id] : null;
    }
}
