package org.tinycore.cloud.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.tinycore.cloud.menu.CloudLinkMenu;
import org.tinycore.cloud.registry.ModBlockEntities;

/**
 * <b>TC Cloud Link</b>: o ponto de acesso à nuvem no mundo. Liga-se à rede AE2 por qualquer lado e expõe o canal
 * do dono como armazenamento (modo e prioridade na tela). Só quem colocou abre a tela. Quebrar o bloco não
 * derruba itens: eles estão na nuvem.
 *
 * <p>{@code BaseEntityBlock} é o bloco "com block entity" do Minecraft; o {@code codec()} é exigido pelo 1.21
 * para blocos registrados (descreve como recriar o bloco a partir das propriedades).
 */
public class CloudLinkBlock extends BaseEntityBlock {

    public static final MapCodec<CloudLinkBlock> CODEC = simpleCodec(CloudLinkBlock::new);

    public CloudLinkBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // BaseEntityBlock é invisível por padrão
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CloudLinkBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                          BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.CLOUD_LINK.get(), (l, p, s, be) -> be.serverTick());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof CloudLinkBlockEntity link) {
            link.setOwner(player);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof CloudLinkBlockEntity link) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }
        if (!link.isOwner(player)) {
            player.displayClientMessage(Component.translatable("gui.tccloud.not_owner", link.ownerName()), true);
            return InteractionResult.CONSUME;
        }
        CloudLinkMenu.open(sp, link);
        return InteractionResult.CONSUME;
    }
}
