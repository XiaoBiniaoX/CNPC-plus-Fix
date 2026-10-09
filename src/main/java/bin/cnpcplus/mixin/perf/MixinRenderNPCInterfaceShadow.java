package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.perf.client.ShadowRadiusAccess;
import bin.cnpcplus.perf.client.ShadowRadiusLimit;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 限制 NPC 阴影开销，保留用户配置。
 * 当前 CNPC 的 PUTFIELD owner 是 RenderNPCInterface，不是字段声明类 EntityRenderer。
 * owner/字段名已对 1.21.1.20251230 生产 JAR 检查
 * （offset 9 = isKilled 时置 0，clamp(0)=0 行为不变；offset 333 = getBbWidth*0.8）；
 * 1.21.1 生产类文件使用 mojmap 字段名 shadowRadius。
 * require=0 允许不兼容版本回退原状。
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public abstract class MixinRenderNPCInterfaceShadow {
    @Redirect(
        method = "render(Lnoppes/npcs/entity/EntityNPCInterface;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "FIELD", opcode = Opcodes.PUTFIELD,
                 target = "Lnoppes/npcs/client/renderer/RenderNPCInterface;shadowRadius:F"),
        require = 0, remap = false
    )
    private void cnpcplus$clampShadow(RenderNPCInterface<?, ?> self, float value) {
        ((ShadowRadiusAccess) self).cnpcplus$setShadowRadius(ShadowRadiusLimit.clamp(value));
    }
}
