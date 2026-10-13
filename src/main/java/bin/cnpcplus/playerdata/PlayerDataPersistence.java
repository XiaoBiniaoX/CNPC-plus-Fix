package bin.cnpcplus.playerdata;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.controllers.data.PlayerScriptData;

/**
 * 玩家数据（对话/任务/传送点/阵营/邮件等）持久化保障。
 *
 * 阶段 38：哈基彬实测「死亡复活后任务/传送点进度回退」。
 * 根因（只读调查确认）：CNPC 没有 PlayerEvent.Clone 处理，1.12.2 复活创建全新
 * EntityPlayerMP → 新 capability → PlayerData.get 惰性从磁盘加载；而死亡时的
 * save(false) 走 CustomNPCsScheduler 异步写盘，复活读盘可能读到旧数据或空文件。
 *
 * 修复分两层：
 * 1. onClone：死亡复活时把旧实体内存中的 PlayerData NBT 直接 setNBT 到新实体，
 *    跳过磁盘读——消除主嫌疑竞态。
 * 2. onServerTick：按配置间隔（默认 5 分钟）对全部在线玩家调用现成的
 *    PlayerData.save(false)——序列化在服务器线程完成，文件 IO 本身已是异步，
 *    天然满足「异步存档」要求，崩溃最多丢一个间隔的进度。
 */
public class PlayerDataPersistence {

    private int tickCounter = 0;

    /**
     * 死亡复活：旧实体 NBT → 新实体，不走磁盘。
     *
     * 不直接用 PlayerData.get(newPlayer)——那会先触发一次磁盘加载（有竞态窗口），
     * 而是手动初始化 capability 字段后 setNBT，完全绕开 loadPlayerData。
     */
    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath() && !event.getEntityPlayer().world.isRemote) {
            EntityPlayer oldPlayer = event.getOriginal();
            EntityPlayer newPlayer = event.getEntityPlayer();
            PlayerData oldData = PlayerData.get(oldPlayer);
            NBTTagCompound nbt = oldData.getNBT();
            PlayerData newData = newPlayer.getCapability(PlayerData.PLAYERDATA_CAPABILITY, null);
            if (newData == null) {
                return;
            }
            newData.player = newPlayer;
            newData.playerLevel = newPlayer.experienceLevel;
            newData.scriptData = new PlayerScriptData(newPlayer);
            newData.setNBT(nbt);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side != Side.SERVER || event.phase != TickEvent.Phase.END) {
            return;
        }
        int seconds = CnpcPlusConfig.getPlayerDataAutoSaveSeconds();
        if (seconds <= 0) {
            return;
        }
        this.tickCounter++;
        if (this.tickCounter < seconds * 20) {
            return;
        }
        this.tickCounter = 0;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            PlayerData.get(player).save(false);
        }
    }
}
