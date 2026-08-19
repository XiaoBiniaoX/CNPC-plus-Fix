package top.cnpcplus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A2: NPC 可见度「半透明」(visible==2) 真正生效。
 *  getRenderType → entityTranslucent（半透明渲染管线）
 *  render HEAD/RETURN → alpha + blend（覆盖部件）
 */
@Mixin(value = RenderCustomNpc.class, remap = false)
public class MixinRenderCustomNpcTranslucent {

    private static final float ALPHA = 0.5f;

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$translucentRenderType(EntityCustomNpc npc, boolean visible, boolean showInvisible,
                                                boolean p_230496_4_, CallbackInfoReturnable<RenderType> cir) {
        if (npc.display.getVisible() != 2) return;
        net.minecraft.client.player.LocalPlayer pp = net.minecraft.client.Minecraft.getInstance().player;
        if (pp == null || !npc.display.isVisibleTo(pp)) return;
        @SuppressWarnings({"unchecked", "rawtypes"})
        RenderCustomNpc self = (RenderCustomNpc) (Object) this;
        ResourceLocation tex = self.getTextureLocation(npc);
        if (tex != null) {
            cir.setReturnValue(RenderType.entityTranslucent(tex));
        }
    }

    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void cnpcplus$beginTranslucent(EntityCustomNpc npc, float yaw, float partialTicks,
                                           com.mojang.blaze3d.vertex.PoseStack pose,
                                           net.minecraft.client.renderer.MultiBufferSource buf,
                                           int light, CallbackInfo ci) {
        if (npc.display.getVisible() == 2) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, ALPHA);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void cnpcplus$endTranslucent(EntityCustomNpc npc, float yaw, float partialTicks,
                                         com.mojang.blaze3d.vertex.PoseStack pose,
                                         net.minecraft.client.renderer.MultiBufferSource buf,
                                         int light, CallbackInfo ci) {
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }
}