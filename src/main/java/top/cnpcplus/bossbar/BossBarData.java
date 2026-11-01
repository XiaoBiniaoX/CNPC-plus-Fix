package top.cnpcplus.bossbar;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;

public final class BossBarData {
    private static final Map<DataDisplay, BossBarSettings> SETTINGS = new WeakHashMap<>();
    private static final Map<ServerBossEvent, Integer> COLORS = new WeakHashMap<>();
    private BossBarData() {}

    public static synchronized BossBarSettings get(DataDisplay display) {
        return SETTINGS.getOrDefault(display, new BossBarSettings(display.getBossbar(), BossBarSettings.legacyColor(display.getBossColor())));
    }

    public static synchronized void put(EntityNPCInterface npc, BossBarSettings settings) {
        SETTINGS.put(npc.display, settings);
        npc.display.setBossbar(settings.mode());
        npc.updateClient = true;
        if (!npc.isClientSide() && npc.bossInfo != null) {
            COLORS.put(npc.bossInfo, settings.rgb());
            visibility(npc.bossInfo);
        }
    }

    public static synchronized void nativeMode(EntityNPCInterface npc, int mode) {
        BossBarSettings previous = get(npc.display);
        SETTINGS.put(npc.display, new BossBarSettings(mode, previous.rgb()));
        if (!npc.isClientSide() && npc.bossInfo != null) {
            COLORS.put(npc.bossInfo, previous.rgb());
            visibility(npc.bossInfo);
        }
    }

    public static synchronized void sync(ServerBossEvent boss, ServerPlayer player, boolean removed) {
        Integer rgb = COLORS.get(boss);
        if (rgb != null) BossBarPacket.send(player, boss.getId(), rgb, removed);
    }

    public static void visibility(ServerBossEvent boss) {
        for (ServerPlayer player : boss.getPlayers()) sync(boss, player, !boss.isVisible());
    }
}
