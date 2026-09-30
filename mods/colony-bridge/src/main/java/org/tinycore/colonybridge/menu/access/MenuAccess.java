package org.tinycore.colonybridge.menu.access;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.tinycore.colonybridge.menu.tablet.TabletView;

/**
 * "Por onde" uma tela de bloco foi aberta, e portanto quando ela pode continuar aberta:
 * <ul>
 *   <li>{@link BlockAccess} — clique no bloco: jogador perto do bloco (regra do Minecraft);</li>
 *   <li>{@link TabletAccess} — pelo tablet: tablet na mão, ligado a esta colônia, com bateria, bloco carregado e
 *       permissão.</li>
 * </ul>
 * Todo pacote de tela passa por {@code menu.stillValid(player)} ({@code ModNetwork.validMenu},
 * {@code TerminalPackets}), então trocar a regra aqui vale para todas as ações — a segurança continua num
 * lugar só. No cliente a regra não existe ({@link #CLIENT}): quem decide é o servidor.
 */
public interface MenuAccess {

    /** Cliente: sempre válido (o servidor fecha a tela quando deixar de ser). */
    MenuAccess CLIENT = new MenuAccess() {
        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public ContainerLevelAccess levelAccess() {
            return ContainerLevelAccess.NULL;
        }
    };

    /** Chamado pelo servidor a cada tick e a cada pacote da tela: false fecha a tela. */
    boolean stillValid(Player player);

    /** Mundo e posição do bloco (ex.: devolver os itens da bancada do Terminal ao fechar). */
    ContainerLevelAccess levelAccess();

    /** Abas do tablet para a tela, ou null se aberta pelo bloco. */
    default @Nullable TabletView tabletView() {
        return null;
    }

    /** Acesso normal, pelo clique no bloco. */
    static MenuAccess block(BlockEntity blockEntity) {
        return new BlockAccess(blockEntity);
    }
}
