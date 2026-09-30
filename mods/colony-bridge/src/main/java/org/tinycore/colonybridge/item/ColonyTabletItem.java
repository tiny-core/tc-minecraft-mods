package org.tinycore.colonybridge.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.block.bridge.ColonyBridgeBlockEntity;
import org.tinycore.colonybridge.integration.ColonyAccess;
import org.tinycore.colonybridge.logic.tablet.TabletCharge;
import org.tinycore.colonybridge.menu.tablet.TabletOpener;
import org.tinycore.colonybridge.registry.ModDataComponents;

import java.util.List;

/**
 * TC Colony Tablet: acesso remoto aos blocos de uma colônia (Terminal, Ponte, Abastecedor e painéis), com
 * bateria própria ({@link TabletEnergy}).
 * <ul>
 *   <li><b>Ligar:</b> shift + clique direito numa Ponte grava a colônia dela ({@link TabletLink}); pôr o tablet
 *       no carregador da Ponte também liga. Só quem pode configurar a Ponte consegue ligar.</li>
 *   <li><b>Usar:</b> clique direito no ar abre a tela do Terminal da colônia (ou a primeira disponível), com
 *       abas para os outros blocos ({@code TabletOpener}).</li>
 * </ul>
 * A barra sob o ícone (a mesma da durabilidade) mostra a bateria.
 */
public class ColonyTabletItem extends Item {

    /** Ciano da marca (o mesmo de {@code UiColors.HIGHLIGHT}; fixo aqui porque este código roda no servidor). */
    private static final int BAR_COLOR = 0x22B8E8;

    public ColonyTabletItem(Properties properties) {
        super(properties);
    }

    // ---------------------------------------------------------------- ligação

    /** Shift + clique direito numa Ponte liga o tablet à colônia dela. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()
                || !(level.getBlockEntity(context.getClickedPos()) instanceof ColonyBridgeBlockEntity bridge)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!bridge.canConfigure(player)) {
            player.displayClientMessage(Component.translatable("link.tccolonybridge.no_permission")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        String colony = ColonyAccess.colonyKeyAt(level, bridge.getBlockPos());
        if (colony == null) {
            player.displayClientMessage(Component.translatable("tablet.tccolonybridge.no_colony")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        String name = ColonyAccess.colonyNameAt(level, bridge.getBlockPos());
        link(context.getItemInHand(), colony, name);
        player.displayClientMessage(Component.translatable("tablet.tccolonybridge.linked", name)
                .withStyle(ChatFormatting.GREEN), true);
        return InteractionResult.SUCCESS;
    }

    /**
     * Grava a colônia no tablet (quem chama já conferiu a permissão). Não mexe se já está ligado a ela com o
     * mesmo nome, para não trocar o item à toa (cada troca é sincronizada com o cliente).
     */
    public static void link(ItemStack stack, String colonyKey, String colonyName) {
        TabletLink link = new TabletLink(colonyKey, colonyName);
        if (!link.equals(link(stack))) {
            stack.set(ModDataComponents.TABLET_LINK.get(), link);
        }
    }

    public static @Nullable TabletLink link(ItemStack stack) {
        return stack.get(ModDataComponents.TABLET_LINK.get());
    }

    // ---------------------------------------------------------------- uso

    /** Clique direito no ar: abre a tela principal da colônia (Terminal; sem ele, a próxima disponível). */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            TabletOpener.open(serverPlayer, hand, null);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * Só "re-equipar" (a animação da mão abaixando e subindo) quando o item muda de verdade. A bateria muda a cada
     * segundo com a tela aberta, e sem isto a mão ficaria balançando atrás da tela.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    // ---------------------------------------------------------------- dica e barra

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        TabletLink link = link(stack);
        tooltip.add(link == null
                ? Component.translatable("tablet.tccolonybridge.not_linked").withStyle(ChatFormatting.YELLOW)
                : Component.translatable("tablet.tccolonybridge.colony", link.colonyName()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tablet.tccolonybridge.energy", TabletEnergy.stored(stack),
                TabletEnergy.capacity()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tablet.tccolonybridge.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return TabletCharge.barWidth(TabletEnergy.stored(stack), TabletEnergy.capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }
}
