package org.tinycore.colonybridge.client.jei;

import org.tinycore.colonybridge.client.encoder.PatternEncoderScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import org.tinycore.colonybridge.ColonyBridgeMod;
import org.tinycore.colonybridge.client.bridge.ColonyBridgeScreen;
import org.tinycore.colonybridge.client.loader.ChunkLoaderScreen;
import org.tinycore.colonybridge.client.supply.ColonySupplyScreen;
import org.tinycore.colonybridge.client.terminal.WarehouseTerminalScreen;
import org.tinycore.core.client.ui.ItemGrid;

import java.util.List;
import java.util.Optional;

/**
 * Integração com o JEI: arrastar itens da lista do JEI para as listas (filtro, Abastecedor) e ghost slots, e o "+" das receitas
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
        registration.addGhostIngredientHandler(ColonyBridgeScreen.class,
                new FilterGhostHandler<>(ColonyBridgeScreen::listWidget, ColonyBridgeScreen::visibleGhostSlots));
        registration.addGhostIngredientHandler(ColonySupplyScreen.class,
                new FilterGhostHandler<>(ColonySupplyScreen::listWidget, screen -> List.of()));
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
        registration.addGuiContainerHandler(ChunkLoaderScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(ChunkLoaderScreen screen) {
                return screen.extraAreas();
            }
        });
        registration.addGuiContainerHandler(PatternEncoderScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(PatternEncoderScreen screen) {
                return screen.extraAreas();
            }
        });
        registration.addGuiContainerHandler(WarehouseTerminalScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(WarehouseTerminalScreen screen) {
                return screen.extraAreas();
            }

            /** Teclas R/U (receita/usos) sobre os itens da grade do armazém, que não são slots de verdade. */
            @Override
            public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(WarehouseTerminalScreen screen,
                                                                                      double mouseX, double mouseY) {
                ItemGrid.Hit hit = screen.gridHit(mouseX, mouseY);
                if (hit == null) {
                    return Optional.empty();
                }
                return registration.getJeiHelpers().getIngredientManager()
                        .createClickableIngredient(VanillaTypes.ITEM_STACK, hit.item(), new Rect2i(hit.x(), hit.y(), 16, 16), false)
                        .map(ingredient -> ingredient);
            }
        });
    }
}
