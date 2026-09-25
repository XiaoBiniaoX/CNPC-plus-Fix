package top.cnpcplus.mixin;

import net.minecraft.world.phys.Vec3;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 移除官方潜行动作额外的 -0.125 render offset，避免按 NPC 大小比例遁地。 */
@Mixin(value = RenderCustomNpc.class, remap = false)
public abstract class MixinRenderCustomNpcCrawlOffset {
    @Inject(method = "getRenderOffset(Lnoppes/npcs/entity/EntityCustomNpc;F)Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true, remap = false)
    private void cnpcplus$keepCrawlFeetAboveGround(EntityCustomNpc npc, float partialTicks,
                                                    CallbackInfoReturnable<Vec3> cir) {
        if (npc == null || !npc.m_6047_() || npc.display == null) return;
        Vec3 offset = cir.getReturnValue();
        if (offset == null) return;
        double correction = 0.125D * npc.display.getSize() / 5.0D;
        cir.setReturnValue(new Vec3(offset.x, offset.y + correction, offset.z));
    }
}
