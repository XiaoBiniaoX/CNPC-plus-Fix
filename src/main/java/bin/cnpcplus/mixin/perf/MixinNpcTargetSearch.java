package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.player.EntityPlayer;
import noppes.npcs.ai.target.EntityAIClosestTarget;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 保留 CNPC 原有随机错峰频率；参考版已移除会延迟重索敌的固定时间门。 */
@Mixin(value = EntityAIClosestTarget.class, remap = false)
public abstract class MixinNpcTargetSearch extends EntityAITarget {
    @Shadow private EntityNPCInterface npc;
    protected MixinNpcTargetSearch(EntityCreature owner, boolean sight) { super(owner, sight); }

    @Inject(method = "func_75250_a()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$skipImpossibleSearch(CallbackInfoReturnable<Boolean> cir) {
        if (!CnpcPlusConfig.isTargetSearchOptimized() || npc.world.isRemote || npc.getAttackTarget() != null
                || npc.advanced.attackOtherFactions || npc.advanced.job == 3 || npc.advanced.role == 6) return;
        for (EntityPlayer player : npc.world.playerEntities) if (player.isEntityAlive() && npc.isInRange(player, npc.stats.aggroRange)) return;
        cir.setReturnValue(false);
    }

    @Redirect(method = "func_75250_a()Z", at = @At(value = "INVOKE",
            target = "Ljava/util/Collections;sort(Ljava/util/List;Ljava/util/Comparator;)V"), require = 1)
    private void cnpcplus$nearestWithoutFullSort(List list, Comparator comparator) {
        if (!CnpcPlusConfig.isTargetSearchOptimized()) { Collections.sort(list, comparator); return; }
        if (list.isEmpty()) return;
        int best = 0;
        for (int i = 1; i < list.size(); i++) if (comparator.compare(list.get(i), list.get(best)) < 0) best = i;
        Collections.swap(list, 0, best);
    }

    @Override public boolean shouldContinueExecuting() {
        return (!CnpcPlusConfig.isTargetSearchOptimized() || npc.getAttackTarget() != null) && super.shouldContinueExecuting();
    }
}
