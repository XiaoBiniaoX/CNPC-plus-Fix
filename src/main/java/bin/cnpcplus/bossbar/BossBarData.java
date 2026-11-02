package bin.cnpcplus.bossbar;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.BossInfoServer;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;

public final class BossBarData {
    private static final Map<DataDisplay, BossBarSettings> SETTINGS = new WeakHashMap<>();
    private static final Map<BossInfoServer, Integer> COLORS = new WeakHashMap<>();
    private BossBarData() { }
    public static BossBarSettings get(DataDisplay display) {
        return SETTINGS.getOrDefault(display, new BossBarSettings(display.getBossbar(), BossBarSettings.legacyColor(display.getBossColor())));
    }
    public static void put(EntityNPCInterface npc, BossBarSettings settings) {
        SETTINGS.put(npc.display, settings);
        npc.display.setBossbar(settings.mode); npc.updateClient = true;
        if (!npc.world.isRemote) {
            COLORS.put(npc.bossInfo, settings.rgb);
            for (EntityPlayerMP player : npc.bossInfo.getPlayers()) sync(npc.bossInfo, player, false);
        }
    }
    public static void nativeMode(EntityNPCInterface npc) {
        SETTINGS.put(npc.display, new BossBarSettings(npc.display.getBossbar(), get(npc.display).rgb));
    }
    public static void sync(BossInfoServer boss, EntityPlayerMP player, boolean removed) {
        Integer rgb = COLORS.get(boss);
        if (rgb != null) bin.cnpcplus.craftingview.network.CraftingViewNetwork.CHANNEL.sendTo(
                new BossBarPacket(boss.getUniqueId(), rgb, removed), player);
    }
}
