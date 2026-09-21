package top.cnpcplus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** floor(y+0.5) 会把低矮家具上的 NPC 起点放进家具所在的阻塞格。 */
@Mixin(WalkNodeEvaluator.class)
public abstract class MixinWalkNodeEvaluatorNpcStart extends NodeEvaluator {
    @Shadow protected abstract Node getStartNode(BlockPos pos);
    @Shadow protected abstract boolean canStartAt(BlockPos pos);

    @Inject(method = "getStart", at = @At("RETURN"), cancellable = true)
    private void cnpcplus$startAboveSupport(CallbackInfoReturnable<Node> cir) {
        if (!(mob instanceof EntityNPCInterface) || !mob.onGround() || mob.isInWater()) return;
        Node old = cir.getReturnValue();
        int y = Mth.ceil(mob.getY());
        if (old == null || old.costMalus >= 0 || y <= old.y) return;
        BlockPos pos = BlockPos.containing(mob.getX(), y, mob.getZ());
        if (level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) return;
        if (!level.noCollision(mob, mob.getBoundingBox().deflate(1.0E-5))) return;
        if (canStartAt(pos)) cir.setReturnValue(getStartNode(pos));
    }
}
