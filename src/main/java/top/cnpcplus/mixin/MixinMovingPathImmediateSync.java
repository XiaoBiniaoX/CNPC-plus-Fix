package top.cnpcplus.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.items.ItemNpcMovingPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemNpcMovingPath.class, remap = false)
public abstract class MixinMovingPathImmediateSync {
    @Shadow private EntityNPCInterface getNpc(ItemStack stack, Level level) { throw new AssertionError(); }

    @Inject(method = "m_6225_", at = @At("RETURN"), require = 1)
    private void cnpcplus$publishNewPoint(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() == InteractionResult.SUCCESS) {
            // Native success means the authorized, server-side NPC was found and the point was added.
            // Send once now, rather than waiting for the NPC's periodic updateClient check.
            getNpc(context.getItemInHand(), context.getLevel()).updateClient();
        }
    }
}
