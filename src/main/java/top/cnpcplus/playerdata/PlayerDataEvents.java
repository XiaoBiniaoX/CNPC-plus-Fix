package top.cnpcplus.playerdata;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerScriptData;

/**
 * 玩家数据防回退核心：
 * <ul>
 *   <li>PlayerEvent.Clone —— 复活/过维度时把旧玩家内存 NBT 直接拷给新玩家，
 *       消除「复活=重读磁盘」竞态（问题2 P0-A/P0-B）；</li>
 *   <li>ServerTick 错峰自动保存 —— 每 5 分钟把在线玩家落盘一次兜底。</li>
 * </ul>
 */
public final class PlayerDataEvents {
    private static final int AUTOSAVE_INTERVAL = 6000; // 5min @20tps
    private static int tickCounter = 0;

    private PlayerDataEvents() {}

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity().level().isClientSide) return;
        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();
        try {
            PlayerData oldData = PlayerData.get(oldPlayer);
            if (oldData == null) return;
            var nbt = oldData.getNBT();

            // 新玩家 capability 已由 AttachCapabilitiesEvent 挂上；不要走 get()（会读盘）
            PlayerData newData = newPlayer.getCapability(PlayerData.PLAYERDATA_CAPABILITY, null).orElse(null);
            if (newData == null || newData == oldData) return;
            newData.player = newPlayer;
            newData.playerLevel = newPlayer.experienceLevel;
            newData.scriptData = new PlayerScriptData(newPlayer);
            newData.setNBT(nbt);
            // 强制下一 tick 同步到客户端（P2-B：清掉客户端死前静态单例的显示残留）
            newData.updateClient = true;
        } catch (Throwable t) {
            noppes.npcs.shared.common.util.LogWriter.except(t);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        tickCounter++;
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) return;
        // 错峰：每位玩家落在不同 tick，避免同 tick 全量写盘
        for (int i = 0; i < players.size(); i++) {
            if (Math.floorMod(tickCounter + i, AUTOSAVE_INTERVAL) == 0) {
                try {
                    PlayerData.get(players.get(i)).save(false);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
