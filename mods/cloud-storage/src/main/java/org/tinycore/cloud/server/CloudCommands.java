package org.tinycore.cloud.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /tccloud checkpoint} (só operador nível 3): salva o mundo com flush e torna durável tudo o que está
 * pendente na nuvem. O backup a quente do TCMine roda isto pelo RCON entre o {@code save-all flush} e a cópia,
 * para o zip sair com o diário coerente com os chunks (plano §5.2).
 */
final class CloudCommands {

    private CloudCommands() {}

    static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tccloud")
                .requires(source -> source.hasPermission(3))
                .then(Commands.literal("checkpoint").executes(ctx -> {
                    CloudService service = CloudService.get();
                    if (service == null) {
                        ctx.getSource().sendFailure(Component.translatable("command.tccloud.disabled"));
                        return 0;
                    }
                    service.checkpointNow();
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.tccloud.checkpoint.done"), true);
                    return 1;
                })));
    }
}
