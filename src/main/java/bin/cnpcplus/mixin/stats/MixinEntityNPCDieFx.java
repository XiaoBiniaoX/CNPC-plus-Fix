package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.fx.FxEngine;
import bin.cnpcplus.fx.FxStore;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCDieFx {
    @Unique private boolean cnpcplus$deathFxPlayed;

    @Inject(method = "func_70645_a", at = @At("RETURN"), require = 1)
    private void cnpcplus$spawnDeathFx(DamageSource source, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (!(npc.world instanceof WorldServer) || npc.getHealth() > 0 || this.cnpcplus$deathFxPlayed) return;
        this.cnpcplus$deathFxPlayed = true;
        FxEngine.spawn((WorldServer) npc.world, npc.posX, npc.posY, npc.posZ, FxStore.get(npc.stats));
    }

    @Inject(method = "reset()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$resetDeathFx(CallbackInfo ci) { this.cnpcplus$deathFxPlayed = false; }
}
