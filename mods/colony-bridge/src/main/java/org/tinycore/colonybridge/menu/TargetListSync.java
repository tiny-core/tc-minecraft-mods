package org.tinycore.colonybridge.menu;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.tinycore.colonybridge.logic.target.TargetLine;
import org.tinycore.colonybridge.logic.target.TargetList;
import org.tinycore.colonybridge.logic.target.TargetListHost;
import org.tinycore.colonybridge.logic.target.TargetListKind;
import org.tinycore.colonybridge.network.TargetListPayload;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * As listas de linhas de uma tela aberta, dos dois lados:
 * <ul>
 *   <li><b>servidor</b> ({@link #sendChanged}): manda a lista ao cliente ao abrir e depois só quando ela
 *       muda (compara a {@link TargetList#version()}), então editar não gera tráfego contínuo;</li>
 *   <li><b>cliente</b> ({@link #lines}, {@link #accept}): guarda a última versão recebida para a tela desenhar.
 *       {@link #revision()} muda a cada pacote, para a tela saber quando reler.</li>
 * </ul>
 * Cada menu com listas (Abastecedor, Ponte) tem um destes.
 */
public final class TargetListSync {

    private final TargetListKind[] kinds;
    /** Servidor: última versão enviada de cada lista (ausente = ainda não enviada). */
    private final Map<TargetListKind, Integer> sentVersions = new EnumMap<>(TargetListKind.class);
    /** Cliente: linhas recebidas. */
    private final Map<TargetListKind, List<TargetLineView>> received = new EnumMap<>(TargetListKind.class);
    private int revision;

    public TargetListSync(TargetListKind... kinds) {
        this.kinds = kinds;
    }

    /** Servidor: envia as listas que mudaram desde o último envio (chamado no {@code broadcastChanges}). */
    public void sendChanged(ServerPlayer player, int containerId, TargetListHost host) {
        for (TargetListKind kind : kinds) {
            TargetList list = host.targetList(kind);
            if (list == null) {
                continue;
            }
            Integer sent = sentVersions.get(kind);
            if (sent != null && sent == list.version()) {
                continue;
            }
            sentVersions.put(kind, list.version());
            List<TargetLineView> views = new ArrayList<>(list.size());
            for (TargetLine line : list.lines()) {
                views.add(TargetLineView.of(line));
            }
            PacketDistributor.sendToPlayer(player, new TargetListPayload(containerId, kind.ordinal(), views));
        }
    }

    /** Cliente: guarda as linhas recebidas de uma lista. */
    public void accept(TargetListKind kind, List<TargetLineView> lines) {
        received.put(kind, List.copyOf(lines));
        revision++;
    }

    /** Cliente: linhas da lista (vazio até o primeiro pacote). */
    public List<TargetLineView> lines(TargetListKind kind) {
        return received.getOrDefault(kind, List.of());
    }

    /** Cliente: muda a cada pacote recebido. */
    public int revision() {
        return revision;
    }
}
