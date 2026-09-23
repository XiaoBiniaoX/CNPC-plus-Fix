package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.common.INpcGroundNavigation;
import net.minecraft.pathfinding.NodeProcessor;
import net.minecraft.pathfinding.WalkNodeProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(WalkNodeProcessor.class)
public abstract class MixinWalkNodeProcessorNpcSurface extends NodeProcessor {
    // A surface below half a block must not put the start inside the solid node.
    @ModifyConstant(method = "getStart", constant = @Constant(doubleValue = 0.5D), require = 1)
    private double cnpcplus$aboveSurface(double original) {
        return entity instanceof INpcGroundNavigation && entity.onGround
                && !entity.isInWater() && !entity.isInLava() ? 0.999999D : original;
    }
}
