package bin.cnpcplus.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import noppes.npcs.controllers.data.PlayerData;

import java.util.UUID;

/**
 * /cnpcplus playerdata load <player> —— 从磁盘强制重载指定玩家的 CNPC 数据。
 *
 * <p>用于任务/传送进度回退后的手动恢复。必须在主线程执行（setNBT 可能 addFreshEntity）。
 */
public final class CmdPlayerData {

    private CmdPlayerData() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(
            Commands.literal("cnpcplus").requires(s -> s.hasPermission(4))
                .then(Commands.literal("playerdata")
                    .then(Commands.literal("load")
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> load(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))))
        );
    }

    private static int load(CommandSourceStack source, ServerPlayer target) {
        UUID uuid = target.getUUID();
        try {
            // 先同步落盘，确保磁盘文件是最新内存状态（异步写队列竞态防护）
            PlayerData current = PlayerData.get(target);
            current.save(false);

            // 从磁盘读取
            net.minecraft.nbt.CompoundTag tag = PlayerData.loadPlayerData(uuid.toString());
            if (tag.isEmpty()) {
                source.sendFailure(Component.literal("磁盘上没有 " + target.getGameProfile().getName() + " 的 CNPC 数据文件"));
                return 0;
            }

            // 应用到内存（主线程）
            current.setNBT(target.registryAccess(), tag);
            current.updateClient = true;

            source.sendSuccess(() -> Component.literal("已从磁盘重载 " + target.getGameProfile().getName() + " 的 CNPC 数据"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("读档失败: " + e.getMessage()));
            return 0;
        }
    }
}
