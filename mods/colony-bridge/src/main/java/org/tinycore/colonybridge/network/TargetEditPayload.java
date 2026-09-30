package org.tinycore.colonybridge.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.logic.target.TargetSpec;

/**
 * Cliente → servidor: uma edição numa lista de linhas (Manter, Excedente ou filtro). Uma mensagem por ação,
 * pequena; o servidor valida tudo ({@code TargetListEditor}): índice, texto, se o alvo existe, limites.
 * <p>
 * Os campos que a operação não usa vão com valor neutro. O item do cursor <b>não</b> vem no pacote
 * ({@link Op#ADD_CURSOR}, {@link Op#SET_CURSOR}): o servidor lê o cursor do próprio jogador. Só o arrastar do
 * JEI manda um item ({@link Op#ADD_STACK}, {@link Op#SET_STACK}) — é só um modelo, nunca vira item de verdade.
 *
 * @param kind  ordinal do {@code TargetListKind}
 * @param op    ordinal de {@link Op}
 * @param index linha editada (ignorado nas operações de adicionar)
 */
public record TargetEditPayload(int containerId, int kind, int op, int index, String text, int amount, boolean all,
                                ItemStack stack) implements CustomPacketPayload {

    /** O que fazer. O ordinal viaja no pacote: só acrescentar valores no fim. */
    public enum Op {
        ADD_TEXT, ADD_CURSOR, ADD_STACK, SET_TEXT, SET_CURSOR, SET_STACK, SET_AMOUNT, REMOVE,
        /** Não edita: a tela trocou de aba (a lista {@code kind} recebe o shift-clique do inventário). */
        SELECT;

        private static final Op[] VALUES = values();

        public static Op byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : null;
        }
    }

    public static final Type<TargetEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "target_edit"));

    /** Mais de 6 campos: o {@code composite} do Minecraft não cobre, então o codec é escrito à mão. */
    public static final StreamCodec<RegistryFriendlyByteBuf, TargetEditPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.kind);
                buf.writeVarInt(p.op);
                buf.writeVarInt(p.index);
                ByteBufCodecs.stringUtf8(TargetSpec.MAX_TEXT).encode(buf, p.text);
                buf.writeVarInt(p.amount);
                buf.writeBoolean(p.all);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, p.stack);
            },
            buf -> new TargetEditPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    ByteBufCodecs.stringUtf8(TargetSpec.MAX_TEXT).decode(buf), buf.readVarInt(), buf.readBoolean(),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));

    /** Atalho para as operações sem item. */
    public static TargetEditPayload of(int containerId, int kind, Op op, int index, String text, int amount,
                                       boolean all) {
        return new TargetEditPayload(containerId, kind, op.ordinal(), index, text, amount, all, ItemStack.EMPTY);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
