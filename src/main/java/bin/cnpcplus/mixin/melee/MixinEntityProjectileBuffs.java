package bin.cnpcplus.mixin.melee;

import bin.cnpcplus.melee.RangedBuffStore;
import bin.cnpcplus.fx.FxStore;
import bin.cnpcplus.fx.FxEngine;
import bin.cnpcplus.fx.FxConfig;
import bin.cnpcplus.fx.FxShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import noppes.npcs.entity.EntityProjectile;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 发射快照和爆炸效果；直击共用穿透模块已核实的伤害成功返回点。 */
@Mixin(value = EntityProjectile.class, remap = false)
public abstract class MixinEntityProjectileBuffs {
    @Unique private long cnpcplus$nextTrailTick;
    @Unique private boolean cnpcplus$trailStopped;
    @Inject(method = "getStatProperties", at = @At("TAIL"), require = 1)
    private void cnpcplus$snapshotRangedBuffs(DataRanged ranged, CallbackInfo ci) {
        RangedBuffStore.snapshot(ranged, (EntityProjectile) (Object) this);
        FxStore.put(this, FxStore.get(ranged));
        FxStore.follow(this, FxStore.follow(ranged));
    }

    @Inject(method = "func_70014_b", at = @At("TAIL"), require = 1)
    private void cnpcplus$writeRangedBuffs(NBTTagCompound tag, CallbackInfo ci) {
        RangedBuffStore.write(this, tag);
        FxStore.write(this, tag, FxStore.PROJECTILE);
        FxStore.writeFollow(this, tag);
        tag.setBoolean("CNPCPlusTrailStopped", this.cnpcplus$trailStopped);
    }

    @Inject(method = "func_70037_a", at = @At("TAIL"), require = 1)
    private void cnpcplus$readRangedBuffs(NBTTagCompound tag, CallbackInfo ci) {
        RangedBuffStore.read(this, tag);
        FxStore.read(this, tag, FxStore.PROJECTILE);
        FxStore.readFollow(this, tag);
        this.cnpcplus$trailStopped = tag.getBoolean("CNPCPlusTrailStopped");
    }

    @Inject(method = "func_70071_h_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$projectileTrail(CallbackInfo ci) {
        EntityProjectile self = (EntityProjectile) (Object) this;
        if (!(self.world instanceof WorldServer) || self.isDead || this.cnpcplus$trailStopped) return;
        long now = self.world.getTotalWorldTime();
        if (now < this.cnpcplus$nextTrailTick || FxStore.get(self).isEmpty()) return;
        int duration = 1;
        for (FxConfig config : FxStore.get(self)) for (FxShape shape : config.shapes) duration = Math.max(duration, shape.duration());
        if (FxStore.follow(self)) FxEngine.spawnFollow((WorldServer) self.world, self, FxStore.get(self));
        else FxEngine.spawn((WorldServer) self.world, self.posX, self.posY, self.posZ, FxStore.get(self));
        this.cnpcplus$nextTrailTick = now + duration;
    }

    @Inject(method = "func_70184_a", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/EventHooks;onProjectileImpact(Lnoppes/npcs/entity/EntityProjectile;Lnoppes/npcs/api/event/ProjectileEvent$ImpactEvent;)V"), require = 1)
    private void cnpcplus$stopTrailAtImpact(net.minecraft.util.math.RayTraceResult hit, CallbackInfo ci) {
        this.cnpcplus$trailStopped = true;
    }

    @Redirect(method = "func_70184_a", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;func_72885_a(Lnet/minecraft/entity/Entity;DDDFZZ)Lnet/minecraft/world/Explosion;"), require = 1)
    private Explosion cnpcplus$applyExplosionBuffs(World world, Entity source, double x, double y, double z,
                                                   float strength, boolean fire, boolean terrain) {
        return RangedBuffStore.explode((EntityProjectile) (Object) this, world, source, x, y, z, strength, fire, terrain);
    }
}
