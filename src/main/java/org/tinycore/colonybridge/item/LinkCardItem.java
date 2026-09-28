package org.tinycore.colonybridge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.Config;
import org.tinycore.colonybridge.block.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.block.monitor.MonitorBlockEntity;

import java.util.List;

/**
 * Cartão de Ligação: grava uma ponte (shift + clique direito nela) e liga monitores a ela
 * (clique direito no monitor).
 * <p>
 * A posição fica no componente {@code CUSTOM_DATA} do item — no 1.21 os itens guardam dados em
 * "data components" em vez de NBT solto; {@code CUSTOM_DATA} é o componente genérico para dados de mods.
 * <p>
 * Toda a validação é no servidor: mesma dimensão, ponte existente e carregada, distância máxima
 * ({@code monitorLinkRange}) e permissão do jogador para configurar a ponte. Assim ninguém exibe
 * os dados da colônia de outro jogador.
 */
public class LinkCardItem extends Item {

    public LinkCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (level.isClientSide || player == null) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        if (level.getBlockEntity(pos) instanceof ColonyBridgeBlockEntity bridge && player.isShiftKeyDown()) {
            return saveBridge(stack, bridge, player, level);
        }
        if (level.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
            return linkMonitor(stack, monitor, player, level);
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult saveBridge(ItemStack stack, ColonyBridgeBlockEntity bridge, Player player,
                                                Level level) {
        if (!bridge.canConfigure(player)) {
            return fail(player, "link.tccolonybridge.no_permission");
        }
        CompoundTag tag = new CompoundTag();
        tag.putLong("bridge", bridge.getBlockPos().asLong());
        tag.putString("dimension", level.dimension().location().toString());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        BlockPos p = bridge.getBlockPos();
        player.displayClientMessage(Component.translatable("link.tccolonybridge.saved", p.getX(), p.getY(), p.getZ())
                .withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult linkMonitor(ItemStack stack, MonitorBlockEntity clicked, Player player,
                                                 Level level) {
        BlockPos bridgePos = storedBridge(stack);
        if (bridgePos == null) {
            return fail(player, "link.tccolonybridge.empty");
        }
        if (!level.dimension().location().toString().equals(storedDimension(stack))) {
            return fail(player, "link.tccolonybridge.wrong_dimension");
        }
        if (!level.isLoaded(bridgePos) || !(level.getBlockEntity(bridgePos) instanceof ColonyBridgeBlockEntity bridge)) {
            return fail(player, "link.tccolonybridge.not_found");
        }
        if (!bridge.canConfigure(player)) {
            return fail(player, "link.tccolonybridge.no_permission");
        }
        BlockPos masterPos = clicked.getMasterPos();
        if (!(level.getBlockEntity(masterPos) instanceof MonitorBlockEntity master)) {
            return fail(player, "link.tccolonybridge.not_found");
        }
        int range = Config.MONITOR_LINK_RANGE.get();
        if (masterPos.distSqr(bridgePos) > (double) range * range) {
            return fail(player, Component.translatable("link.tccolonybridge.too_far", range));
        }
        master.setLink(bridgePos);
        player.displayClientMessage(Component.translatable("link.tccolonybridge.linked")
                .withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult fail(Player player, String key) {
        return fail(player, Component.translatable(key));
    }

    private static InteractionResult fail(Player player, Component message) {
        player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        return InteractionResult.FAIL;
    }

    private static @Nullable BlockPos storedBridge(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("bridge") ? BlockPos.of(tag.getLong("bridge")) : null;
    }

    private static String storedDimension(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("dimension");
    }

    /** Brilho de encantamento quando há uma ponte gravada. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return storedBridge(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlockPos pos = storedBridge(stack);
        tooltip.add(pos == null
                ? Component.translatable("tooltip.tccolonybridge.link_card.empty").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.tccolonybridge.link_card.bridge", pos.getX(), pos.getY(), pos.getZ())
                        .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.tccolonybridge.link_card.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
