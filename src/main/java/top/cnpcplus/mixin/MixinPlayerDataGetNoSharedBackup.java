package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Player;
import noppes.npcs.CustomNpcs;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerScriptData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * P2-A（低概率高危）：PlayerData.get 的 orElse(backup) 共享静态兜底串档。
 * capability 缺失时所有玩家共享同一个 backup 对象，data.player==null 触发
 * 磁盘加载写进全局共享实例并交叉污染。改为缺失时用全新 PlayerData。
 * 客户端路径保持走 proxy.getPlayerData。
 */
@Mixin(value = PlayerData.class, remap = false)
public abstract class MixinPlayerDataGetNoSharedBackup {

    /**
     * @author cnpcplus
     * @reason no shared static backup — fresh PlayerData when capability missing
     */
    @Overwrite
    public static PlayerData get(Player player) {
        if (player.level().isClientSide) {
            return CustomNpcs.proxy.getPlayerData(player);
        }

        PlayerData data = player.getCapability(PlayerData.PLAYERDATA_CAPABILITY, null)
                .orElseGet(PlayerData::new);
        if (data.player == null) {
            data.player = player;
            data.playerLevel = player.experienceLevel;
            data.scriptData = new PlayerScriptData(player);
            net.minecraft.nbt.CompoundTag compound = PlayerData.loadPlayerData(player.getUUID().toString());
            data.setNBT(compound);
        }
        return data;
    }
}
