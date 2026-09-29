package org.tinycore.colonybridge.client.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.client.bridge.ColonyBridgeScreen;
import org.tinycore.colonybridge.client.supply.ColonySupplyScreen;
import org.tinycore.colonybridge.client.terminal.WarehouseTerminalScreen;

import java.util.List;

/**
 * Integração com o JEI: arrastar itens da lista do JEI para os ghost slots do filtro e o "+" das receitas
 * de bancada no Terminal do Armazém.
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

    /**
     * Arrastar itens para os ghost slots da ponte, e as barras laterais (fora da janela) das telas
     * como "áreas extras", para o JEI não desenhar a lista de itens por cima delas.
     */
    /** "+" nas receitas de bancada monta a receita na grade do Terminal do Armazém. */
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new TerminalRecipeTransfer(registration.getTransferHelper()),
                RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(ColonyBridgeScreen.class, new FilterGhostHandler());
        registration.addGuiContainerHandler(ColonyBridgeScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(ColonyBridgeScreen screen) {
                return screen.extraAreas();
            }
        });
        registration.addGuiContainerHandler(ColonySupplyScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(ColonySupplyScreen screen) {
                return screen.extraAreas();
            }
        });
        registration.addGuiContainerHandler(WarehouseTerminalScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(WarehouseTerminalScreen screen) {
                return screen.extraAreas();
            }
        });
    }
}
