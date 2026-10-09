package top.cnpcplus.mixin;

import top.cnpcplus.perf.client.ShadowRadiusAccess;
import top.cnpcplus.perf.client.ShadowRadiusLimit;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 限制 NPC 阴影开销，保留用户配置。
 * 当前 CNPC 的 PUTFIELD owner 是 RenderNPCInterface，不是字段声明类 EntityRenderer。
 * owner/字段名已对实际 20260711 生产 JAR 检查；require=0 允许不兼容版本回退原状。
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public abstract class MixinRenderNPCInterfaceShadowClamp {
    @Redirect(
        method = "render(Lnoppes/npcs/entity/EntityNPCInterface;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
                 target = "Lnoppes/npcs/client/renderer/RenderNPCInterface;f_114477_:F"),
        require = 0, remap = false
    )
    private void cnpcyouhua$clampShadow(RenderNPCInterface<?, ?> self, float value) {
        ((ShadowRadiusAccess) self).cnpcyouhua$setShadowRadius(ShadowRadiusLimit.clamp(value));
    }
}
