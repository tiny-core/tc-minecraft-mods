package org.tinycore.colonybridge.logic.bridge;

import appeng.api.stacks.AEItemKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.integration.OpenRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Relatório do último ciclo: o que aconteceu com cada pedido. Alimenta a tela da ponte.
 * <p>
 * Guarda no máximo {@link #MAX_LINES} linhas (limite de tamanho do pacote enviado ao cliente),
 * mas conta o total para a tela mostrar "+N pedidos".
 */
public final class CycleReport {

    /** Teto de linhas sincronizadas com o cliente (ver regras de segurança no CLAUDE.md). */
    public static final int MAX_LINES = 40;

    private final List<RequestLine> lines = new ArrayList<>();
    private int total;

    void clear() {
        lines.clear();
        total = 0;
    }

    /**
     * @param chosen item que a ponte escolheu craftar para um pedido por tag (ou null): vira o ícone e
     *               entra na descrição, ex.: "Qualquer picareta → Picareta de Pedra"
     */
    void add(OpenRequest request, RequestOutcome outcome, @Nullable AEItemKey chosen) {
        total++;
        if (lines.size() >= MAX_LINES) {
            return;
        }
        if (chosen == null) {
            lines.add(new RequestLine(request.icon(), request.amount(), outcome, request.label()));
            return;
        }
        ItemStack icon = chosen.toStack();
        Component label = Component.translatable("gui.tccolonybridge.crafting_as", request.label(), icon.getHoverName());
        lines.add(new RequestLine(icon, request.amount(), outcome, label));
    }

    /** Cópia imutável das linhas (seguro para guardar no snapshot). */
    public List<RequestLine> lines() {
        return List.copyOf(lines);
    }

    public int total() {
        return total;
    }
}
