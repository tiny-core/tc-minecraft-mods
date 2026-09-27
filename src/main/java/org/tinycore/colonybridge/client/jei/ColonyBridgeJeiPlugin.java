package org.tinycore.colonybridge.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.client.ColonyBridgeScreen;

/**
 * Integração com o JEI: permite arrastar itens da lista do JEI para os ghost slots do filtro.
 * <p>
 * O JEI encontra esta classe pela anotação {@code @JeiPlugin} e só a carrega se estiver instalado;
 * nenhuma outra classe do mod referencia o JEI, então ele continua opcional. Fica em {@code client/}
 * porque o JEI só existe no cliente.
 */
@JeiPlugin
public class ColonyBridgeJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ColonyBridgeMod.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(ColonyBridgeScreen.class, new FilterGhostHandler());
    }
}
