package bin.cnpcplus.mixin.lifecycle;

import bin.cnpcplus.data.NpcHitboxStore;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native entity collision collection supplies a solid wall without a world scan. */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinNpcCollisionMode extends EntityCreature {
    protected MixinNpcCollisionMode(World world) { super(world); }
    @Override public AxisAlignedBB getCollisionBoundingBox() {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        return NpcHitboxStore.isSolid(npc.display) && !npc.isKilled() && npc.width > 1.0E-5f
                ? getEntityBoundingBox() : super.getCollisionBoundingBox();
    }
    @Override protected void collideWithNearbyEntities() {
        if (NpcHitboxStore.mode(((EntityNPCInterface) (Object) this).display) == 0) super.collideWithNearbyEntities();
    }
    @Override public void applyEntityCollision(Entity other) {
        if (NpcHitboxStore.mode(((EntityNPCInterface) (Object) this).display) == 0) super.applyEntityCollision(other);
    }
    @Inject(method = "func_70104_M", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$noPush(CallbackInfoReturnable<Boolean> cir) {
        if (NpcHitboxStore.isSolid(((EntityNPCInterface) (Object) this).display)) cir.setReturnValue(false);
    }
    @Inject(method = "func_184192_z", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$piston(CallbackInfoReturnable<EnumPushReaction> cir) {
        if (NpcHitboxStore.isSolid(((EntityNPCInterface) (Object) this).display)) cir.setReturnValue(EnumPushReaction.IGNORE);
    }
}
