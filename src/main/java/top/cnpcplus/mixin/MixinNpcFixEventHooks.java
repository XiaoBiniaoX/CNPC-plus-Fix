package top.cnpcplus.mixin;

import noppes.npcs.CustomNpcs;
import noppes.npcs.EventHooks;
import noppes.npcs.entity.EntityNPCInterface;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.ai.NpcAiOptimizerAccess;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = EventHooks.class, remap = false)
public abstract class MixinNpcFixEventHooks {
    /** 240d 测试日志（定位后整段删除）。 */
    @Unique
    private static final org.slf4j.Logger cnpcplus$TEST_LOG = LoggerFactory.getLogger("cnpcplus");

    @Unique
    private static final ConcurrentHashMap<Integer, long[]> cnpcplus$HB = new ConcurrentHashMap<>();

    @Inject(method = "onNPCTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$throttleNpcUpdate(EntityNPCInterface npc, CallbackInfo ci) {
        cnpcplus$npcTickHeartbeat(npc);
        if (!NpcAiOptimizerAccess.enabled() || npc == null) return;
        if (!NpcAiOptimizerAccess.updateWhenScriptsDisabled()
                && !NpcAiOptimizerAccess.scriptsEnabled(npc)) {
            ci.cancel();
            return;
        }
        int interval = NpcAiOptimizerAccess.updateInterval();
        if (interval > 10 && !NpcAiOptimizerAccess.shouldRun(npc, interval)) ci.cancel();
    }

    /** 240d 测试日志：NPC 心跳（onNPCTick 每 10t 必经，FreezeNPCs 下也走 baseTick）。 */
    @Unique
    private static void cnpcplus$npcTickHeartbeat(EntityNPCInterface npc) {
        if (npc == null || npc.level() == null || npc.level().isClientSide()) return;
        long now = npc.level().getGameTime();
        long[] st = cnpcplus$HB.computeIfAbsent(npc.getId(), k -> new long[]{now, 0L});
        if (st[1] >= 5L && now - st[0] < 200L) return;
        st[0] = now;
        st[1]++;
        int tgtGoals = -1;
        int goals = -1;
        try {
            MobGoalSelectorAccess access = (MobGoalSelectorAccess) (Object) npc;
            GoalSelector ts = access.cnpcplus$getTargetSelector();
            GoalSelector gs = access.cnpcplus$getGoalSelector();
            if (ts != null) tgtGoals = ts.getAvailableGoals().size();
            if (gs != null) goals = gs.getAvailableGoals().size();
        } catch (Throwable ignored) {
        }
        cnpcplus$TEST_LOG.warn(
                "[tgt-test] npcTick id={} t={} aggro={} faction={} updAI={} killed={} ride={} tgt={} freeze={} noAi={} dying={} effAi={} tgtGoals={} goals={}",
                npc.getId(), now,
                npc.stats == null ? -1 : npc.stats.aggroRange,
                npc.faction == null ? "null" : npc.faction.name,
                npc.updateAI, npc.isKilled(), npc.isPassenger(), npc.getTarget(),
                CustomNpcs.FreezeNPCs,
                npc.isNoAi(), npc.isDeadOrDying(), npc.isEffectiveAi(),
                tgtGoals, goals);
    }
}
