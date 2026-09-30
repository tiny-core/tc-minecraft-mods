package org.tinycore.colonybridge.block.bridge;

import appeng.api.networking.IGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.integration.ae2.GridPower;
import org.tinycore.colonybridge.item.TabletEnergy;
import org.tinycore.colonybridge.logic.tablet.TabletCharge;
import org.tinycore.colonybridge.registry.ModItems;

import java.util.function.Consumer;

/**
 * Carregador do tablet na Ponte: um slot que só aceita o TC Colony Tablet e o carrega com energia da rede ME
 * (AE convertido em FE, {@link GridPower}), a {@code tabletChargeRate} FE por tick.
 * <p>
 * Carrega a cada {@link #INTERVAL} ticks (não a cada tick) para não mexer na rede ME à toa. O slot é um
 * {@link ItemStackHandler} (inventário simples do NeoForge); não é exposto a funis/cabos, só à tela da Ponte,
 * que só abre para quem pode configurá-la — por isso pôr o tablet aqui também o liga à colônia
 * ({@code onInserted}). Ao quebrar a Ponte, o tablet cai no chão ({@link #drop}).
 */
public final class TabletCharger {

    private static final int INTERVAL = 10;

    private final Runnable onChanged;
    private final ItemStackHandler slot;
    private int ticks;

    /**
     * @param onChanged  marca o bloco para salvar
     * @param onInserted chamado quando um tablet entra no slot (a Ponte liga o tablet à colônia)
     */
    TabletCharger(Runnable onChanged, Consumer<ItemStack> onInserted) {
        this.onChanged = onChanged;
        this.slot = new ItemStackHandler(1) {
            @Override
            public boolean isItemValid(int index, ItemStack stack) {
                return stack.is(ModItems.COLONY_TABLET.get());
            }

            @Override
            public int getSlotLimit(int index) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int index) {
                onChanged.run();
                ItemStack stack = getStackInSlot(index);
                if (!stack.isEmpty()) {
                    onInserted.accept(stack);
                }
            }
        };
    }

    /** O slot, para o menu da Ponte. */
    public ItemStackHandler handler() {
        return slot;
    }

    /** Um tick do servidor; {@code grid} null = rede ME inativa (não carrega). */
    void tick(@Nullable IGrid grid) {
        if (++ticks < INTERVAL) {
            return;
        }
        ticks = 0;
        ItemStack stack = slot.getStackInSlot(0);
        if (grid == null || stack.isEmpty()) {
            return;
        }
        IEnergyStorage battery = TabletEnergy.storage(stack);
        int wanted = TabletCharge.step(battery.getEnergyStored(), battery.getMaxEnergyStored(),
                Config.TABLET_CHARGE_RATE.get(), INTERVAL);
        int received = battery.receiveEnergy(GridPower.extractFe(grid, wanted), false);
        if (received > 0) {
            onChanged.run(); // a energia mora no item do slot: salvar o bloco e atualizar a tela
        }
    }

    void drop(Level level, BlockPos pos) {
        ItemStack stack = slot.getStackInSlot(0);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
            slot.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    CompoundTag save(HolderLookup.Provider registries) {
        return slot.serializeNBT(registries);
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.isEmpty()) {
            slot.deserializeNBT(registries, tag);
        }
    }
}
