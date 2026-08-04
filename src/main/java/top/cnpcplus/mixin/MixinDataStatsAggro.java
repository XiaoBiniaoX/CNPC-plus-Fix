package top.cnpcplus.mixin;

import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DataStats.class, remap = false)
public class MixinDataStatsAggro {

    @Shadow(remap = false)
    public EntityNPCInterface npc;

    @Inject(method = "setAggroRange", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSetAggroRange(int range, CallbackInfo ci) {
        this.npc.stats.aggroRange = range;
        this.npc.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Math.max(range, 64));
        this.npc.restrictTo(this.npc.ais.startPos(), this.npc.stats.aggroRange * 2);
        ci.cancel();
    }
}
