package top.cnpcplus.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.CustomItems;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A2: 阴影修复 + 半透明渲染。
 * 1) 阴影大小随 display.getSize() 缩放；不可见 NPC 无阴影。
 * 2) 半透明：RenderNPCInterface.renderColor 强制 alpha=1.0，导致 entityTranslucent 无效。
 *    这里在 renderColor 的 setShaderColor 里把 alpha 改为 0.5（当 NPC visible==2 时）。
 * 注入点：RenderNPCInterface.render 中 f_114477_ 赋值处的 getBbWidth()（SRG m_20205_）调用；
 *         RenderNPCInterface.renderColor 中 RenderSystem.setShaderColor 的 alpha 参数。
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public class MixinRenderNPCInterfaceShadow {

    @Unique
    private static final float ALPHA = 0.5f;

    @Unique
    private boolean cnpcplus$translucent = false;

    // render 在生产 jar 里有两个同名重载（泛型桥接 m_7392_ 会被算作候选），
    // 只写 "render" 会匹配到错误的签名并报 Invalid descriptor，必须写全描述符。
    @Redirect(method = "render(Lnoppes/npcs/entity/EntityNPCInterface;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnoppes/npcs/entity/EntityNPCInterface;m_20205_()F"))
    private float cnpcplus$scaledShadow(EntityNPCInterface npc) {
        float sizeRatio = 1.0f;
        if (npc instanceof EntityCustomNpc custom) {
            sizeRatio = Math.max(0.0f, custom.display.getSize() / 5.0f);
        }
        if (!cnpcplus$isVisible(npc)) {
            return 0.0f;
        }
        return npc.getBbWidth() * sizeRatio;
    }

    private static boolean cnpcplus$isVisible(EntityNPCInterface npc) {
        Player p = Minecraft.getInstance().player;
        if (p == null) return true;
        if (p.isCreative() || p.isSpectator()) return true;
        ItemStack hand = p.getMainHandItem();
        if (!hand.isEmpty() && hand.is(CustomItems.wand)) return true;
        return npc.display.isVisibleTo(p);
    }

    // --- 半透明：标记当前 NPC 是否半透明 ---
    @Inject(method = "render(Lnoppes/npcs/entity/EntityNPCInterface;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void cnpcplus$markRender(EntityNPCInterface npc, float entityYaw, float partialTicks,
                                     com.mojang.blaze3d.vertex.PoseStack poseStack,
                                     net.minecraft.client.renderer.MultiBufferSource buffer,
                                     int packedLight, CallbackInfo ci) {
        this.cnpcplus$translucent = npc instanceof EntityCustomNpc custom && custom.display.getVisible() == 2;
    }

    @Inject(method = "renderColor", at = @At("HEAD"))
    private void cnpcplus$markColor(EntityNPCInterface npc, CallbackInfo ci) {
        this.cnpcplus$translucent = npc instanceof EntityCustomNpc custom && custom.display.getVisible() == 2;
    }

    @ModifyArg(method = "renderColor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderColor(FFFF)V"), index = 3)
    private float cnpcplus$colorAlpha(float alpha) {
        return this.cnpcplus$translucent ? ALPHA : alpha;
    }
}