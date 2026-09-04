package bin.cnpcplus.playerdata;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.data.PlayerData;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 以玩家 UUID 为键缓存服务端 PlayerData，替代 CNPC 用实体 ID 作键的实现。
 *
 * <p>1.20.1 的 PlayerData 挂在 Forge Capability 上，随实体生命周期自动销毁。
 * 1.21.1 NeoForge 移除 Capability 后，CNPC 改成了静态
 * {@code Map<Integer, PlayerData>}（键为 {@code player.getId()}），
 * 但没有移植任何销毁逻辑，全代码库对该表只有创建和读取、从不删除。
 *
 * <p>后果是实体 ID 在玩家退出后会被复用：新玩家若拿到某个已退出玩家用过的实体 ID，
 * {@code get()} 中的 {@code if (data.player != null) return data;} 会直接返回那份旧数据，
 * 该玩家自己的存档文件根本不会被读取；退出保存时又会把这份旧内容写回，
 * 表现就是「玩家每次进出，任务等 CNPC 数据被重置」。
 *
 * <p>本类只改键与生命周期，不改 CNPC 的 NBT 结构和存档文件格式，
 * 因此与原版存档、旧存档完全兼容。
 */
public final class PlayerDataStore {

    private static final Map<UUID, PlayerData> BY_UUID = new ConcurrentHashMap<>();

    private PlayerDataStore() {
    }

    /**
     * 取得该玩家的服务端数据；首次取得时从磁盘读取其 UUID 对应的存档。
     *
     * <p>玩家实例会在重生、换维度后被替换，所以每次都刷新 {@code player} 引用，
     * 避免持有已失效的实体导致保存写错对象。
     */
    public static PlayerData get(Player player) {
        UUID id = player.getUUID();
        PlayerData data = BY_UUID.get(id);
        if (data != null) {
            // 同一玩家换了实体实例（重生/换维度）时同步引用，数据本身不重读。
            if (data.player != player) {
                data.player = player;
            }
            return data;
        }

        data = new PlayerData();
        data.player = player;
        data.playerLevel = player.experienceLevel;
        data.scriptData = new noppes.npcs.controllers.data.PlayerScriptData(player);
        CompoundTag compound = PlayerData.loadPlayerData(id.toString());
        data.setNBT(player.registryAccess(), compound);
        BY_UUID.put(id, data);
        return data;
    }

    /** 玩家退出时先落盘再移除缓存，避免旧实体 ID 被复用后串数据。 */
    public static void unload(Player player) {
        PlayerData data = BY_UUID.remove(player.getUUID());
        if (data == null) return;
        // 保存前确保 player 引用有效，getNBT 需要用它取 uuid 与注册表。
        data.player = player;
        data.save(false);
    }
}
