package org.tinycore.cloud.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Conteúdo pelos meios padrão:
 * <ul>
 *   <li>componente {@code minecraft:container} (shulker box e afins);</li>
 *   <li>componente {@code minecraft:bundle_contents} (bundle);</li>
 *   <li>capability de itens do NeoForge ({@code Capabilities.ItemHandler.ITEM}): é por ela que mochilas e
 *       outros mods expõem o que carregam, mesmo quando o conteúdo mora no save do mundo.</li>
 * </ul>
 * "Capability" no NeoForge ≈ uma interface que um objeto pode oferecer opcionalmente, consultada em tempo de
 * execução (parecido com {@code GetService} em C#).
 */
public final class VanillaContentProbe implements ContentProbe {

    @Override
    public boolean hasContents(@NotNull ItemStack stack) {
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyItems().iterator().hasNext()) return true;

        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null && !bundle.isEmpty()) return true;

        IItemHandler handler = stack.getCapability(Capabilities.ItemHandler.ITEM);
        if (handler == null) return false;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }
}
