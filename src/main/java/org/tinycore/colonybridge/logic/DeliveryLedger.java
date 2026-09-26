package org.tinycore.colonybridge.logic;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.tinycore.colonybridge.ColonyBridgeMod;

import java.util.HashMap;
import java.util.Map;

/**
 * Registro compartilhado de quais pedidos da colônia já foram atendidos (ou estão sendo craftados)
 * e por qual ponte.
 * <p>
 * Existe por dois motivos:
 * <ul>
 *   <li><b>Sobreviver a reinícios:</b> o cooldown de reentrega fica salvo no mundo, então reiniciar o
 *       servidor não faz a ponte entregar de novo um pedido que ainda está a caminho do cidadão.</li>
 *   <li><b>Coordenar várias pontes:</b> todas as pontes consultam o mesmo registro, então duas pontes
 *       na mesma colônia não atendem o mesmo pedido.</li>
 * </ul>
 * {@code SavedData} é o mecanismo do Minecraft para guardar dados extras junto do mundo (vira o arquivo
 * {@code data/tccolonybridge_deliveries.dat} do overworld). Parecido com um "singleton persistente": o jogo
 * carrega na primeira chamada de {@link #get} e salva sozinho quando {@code setDirty()} foi chamado.
 * <p>
 * Tudo roda na thread do servidor (os tickers dos blocos são sequenciais), então não há concorrência.
 * Usado por {@link BridgeLogic}; as chaves (colônia e pedido) vêm de {@code integration/}.
 */
public final class DeliveryLedger extends SavedData {

    private static final String FILE_NAME = ColonyBridgeMod.MOD_ID + "_deliveries";
    private static final SavedData.Factory<DeliveryLedger> FACTORY =
            new SavedData.Factory<>(DeliveryLedger::new, DeliveryLedger::load, null);

    /**
     * @param bridge    posição da ponte que reivindicou o pedido ({@code BlockPos.asLong()})
     * @param time      gameTime do registro
     * @param delivered true = itens já entregues (ninguém mexe); false = craft em andamento (só a dona mexe)
     */
    private record Claim(long bridge, long time, boolean delivered) {}

    /** Chave "colônia/pedido" → reivindicação. Mapa plano porque é pequeno e expira sozinho. */
    private final Map<String, Claim> claims = new HashMap<>();

    /** Registro único do servidor, guardado no overworld (as colônias de todas as dimensões cabem nele). */
    public static DeliveryLedger get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    /**
     * true se esta ponte deve pular o pedido: ele já foi entregue (por qualquer ponte)
     * ou outra ponte está craftando para ele.
     */
    public boolean isBlocked(String colony, String request, long bridge) {
        Claim claim = claims.get(key(colony, request));
        return claim != null && (claim.delivered() || claim.bridge() != bridge);
    }

    public void markDelivered(String colony, String request, long bridge, long now) {
        put(colony, request, new Claim(bridge, now, true));
    }

    public void markCrafting(String colony, String request, long bridge, long now) {
        put(colony, request, new Claim(bridge, now, false));
    }

    /**
     * Remove registros mais velhos que {@code maxAge} ticks. Também descarta registros "do futuro"
     * (ex.: mundo restaurado de backup), que senão nunca expirariam.
     */
    public void expire(long now, long maxAge) {
        if (claims.values().removeIf(c -> now - c.time() > maxAge || c.time() > now)) {
            setDirty();
        }
    }

    private void put(String colony, String request, Claim claim) {
        claims.put(key(colony, request), claim);
        setDirty();
    }

    private static String key(String colony, String request) {
        return colony + "/" + request;
    }

    // ---------------------------------------------------------------- NBT

    private static DeliveryLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        DeliveryLedger ledger = new DeliveryLedger();
        ListTag list = tag.getList("claims", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ledger.claims.put(entry.getString("key"),
                    new Claim(entry.getLong("bridge"), entry.getLong("time"), entry.getBoolean("delivered")));
        }
        return ledger;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        claims.forEach((key, claim) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", key);
            entry.putLong("bridge", claim.bridge());
            entry.putLong("time", claim.time());
            entry.putBoolean("delivered", claim.delivered());
            list.add(entry);
        });
        tag.put("claims", list);
        return tag;
    }
}
