package top.cnpcplus.ai;

import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.config.NpcAiOptimizerConfig;

public final class NpcAiOptimizerAccess {
    private NpcAiOptimizerAccess() {}

    public static boolean enabled() { return safeBool(NpcAiOptimizerConfig.ENABLED, true); }
    public static int targetInterval() { return safeInt(NpcAiOptimizerConfig.TARGET_SEARCH_INTERVAL_TICKS, 20); }
    public static boolean staggerTargets() { return safeBool(NpcAiOptimizerConfig.STAGGER_TARGET_SEARCHES, true); }
    public static int maxAggroRange() { return safeInt(NpcAiOptimizerConfig.MAX_EFFECTIVE_AGGRO_RANGE, 0); }
    public static boolean updateWhenScriptsDisabled() { return safeBool(NpcAiOptimizerConfig.UPDATE_EVENT_WHEN_SCRIPTS_DISABLED, true); }
    public static int updateInterval() { return safeInt(NpcAiOptimizerConfig.UPDATE_EVENT_INTERVAL_TICKS, 10); }
    public static boolean collideWhenScriptsDisabled() { return safeBool(NpcAiOptimizerConfig.COLLIDE_SCAN_WHEN_SCRIPTS_DISABLED, true); }
    public static int collideInterval() { return safeInt(NpcAiOptimizerConfig.COLLIDE_SCAN_INTERVAL_TICKS, 4); }
    public static boolean shouldRun(Entity entity, int interval) {
        return entity == null || interval <= 1 || entity.level() == null
            || Math.floorMod(entity.level().getGameTime() + entity.getId(), interval) == 0L;
    }
    public static boolean scriptsEnabled(EntityNPCInterface npc) { return npc != null && npc.script != null && npc.script.getEnabled(); }
    private static boolean safeBool(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value, boolean fallback) { try { return value.get(); } catch (Throwable t) { return fallback; } }
    private static int safeInt(net.minecraftforge.common.ForgeConfigSpec.IntValue value, int fallback) { try { return value.get(); } catch (Throwable t) { return fallback; } }
}
