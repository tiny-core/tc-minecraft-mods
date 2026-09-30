package org.tinycore.colonybridge.menu.tablet;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.logic.tablet.TabletTab;
import org.tinycore.colonybridge.logic.tablet.TabletTabs;

/**
 * O que a tela precisa saber quando foi aberta pelo tablet: quais abas estão disponíveis (máscara de
 * {@link TabletTabs}) e qual está aberta. Vai junto dos "dados extras" da abertura do menu; ausente = tela aberta
 * pelo próprio bloco (sem barra de abas).
 */
public record TabletView(int available, TabletTab active) {

    public boolean isAvailable(TabletTab tab) {
        return TabletTabs.isAvailable(available, tab);
    }

    /** Escreve no fim dos dados de abertura: um booleano "veio do tablet" e, se sim, os dois números. */
    public static void write(RegistryFriendlyByteBuf buf, @Nullable TabletView view) {
        buf.writeBoolean(view != null);
        if (view != null) {
            buf.writeVarInt(view.available);
            buf.writeVarInt(view.active.ordinal());
        }
    }

    /** Lê o que {@link #write} escreveu (no cliente). Aba inválida vira o Terminal. */
    public static @Nullable TabletView read(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            return null;
        }
        int available = buf.readVarInt();
        TabletTab active = TabletTab.byId(buf.readVarInt());
        return new TabletView(available, active == null ? TabletTab.TERMINAL : active);
    }
}
