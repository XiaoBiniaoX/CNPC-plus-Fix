package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import noppes.npcs.CustomItems;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 可见性条件照旧；修复官方无替身分支忽略 visible/showInvisible 的渲染类型。 */
@Mixin(value = RenderCustomNpc.class, remap = false)
public class MixinRenderCustomNpcTranslucent {
    @Inject(method = "render(Lnoppes/npcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void cnpcplus$visibility(EntityCustomNpc npc, float yaw, float ticks, PoseStack pose,
                                    MultiBufferSource buffer, int light, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && !npc.display.isVisibleTo(player) && !player.isSpectator()
                && !player.getMainHandItem().is(CustomItems.wand)) ci.cancel();
    }

    @Inject(method = "getRenderType(Lnoppes/npcs/entity/EntityCustomNpc;ZZZ)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private void cnpcplus$renderType(EntityCustomNpc npc, boolean visible, boolean ghost, boolean outline,
                                    CallbackInfoReturnable<RenderType> cir) {
        RenderCustomNpc renderer = (RenderCustomNpc) (Object) this;
        var texture = renderer.getTextureLocation(npc);
        if (ghost || npc.display.getVisible() == 2) {
            cir.setReturnValue(RenderType.entityTranslucent(texture));
        } else if (!visible) {
            cir.setReturnValue(outline ? RenderType.outline(texture) : null);
        }
    }
}
