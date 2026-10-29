package top.cnpcplus.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.stats.HideDelayStore;

/**
 * 238-1：基础有限渲染器（simpleRender 替身）的尸体不隐藏。
 *
 * <h3>根因</h3>
 * {@code RenderCustomNpc.render(EntityCustomNpc,...) :210-217} 的 simpleRender 分支
 * 画完替身后提前 {@code return}，绕过 {@code RenderNPCInterface.render:131} 的渲染门
 * {@code !isKilled || !hideKilledBody || deathTime <= 20} —— 所以开了「隐藏尸体」的
 * 替身模型 NPC 死后尸体永远可见。
 *
 * <h3>修法</h3>
 * @Redirect 分支内仅有的 {@code EntityRenderer.render}（:185，字节码实证该方法内唯一）
 * 与 {@code renderNameTag}（:199，同唯一），判据与渲染门同源 + 延迟取
 * {@link HideDelayStore}（默认 5，与渲染门 ModifyConstant 同一值）：
 * {@code isKilled && hideKilledBody && deathTime > delay} 时跳过两者（名字也不该留）。
 * {@code deathTime} 走 reobf（先例 MixinEntityNPCKilledBody:127）。
 *
 * <p>{@code require = 1}：两处锚点均已字节码实证唯一，失配必须在启动日志暴露
 * （238b 整份 mixin 因 @Shadow 失配被静默丢弃的教训）。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = RenderCustomNpc.class, remap = false)
public abstract class MixinRenderCustomNpcSimpleRenderHide {

    @Shadow(remap = false)
    private EntityNPCInterface npc;

    /**
     * 必须写完整描述符：{@code RenderCustomNpc} 有多个 {@code render} 重载
     * （EntityNPCInterface/LivingEntity/Entity 桥接 + EntityCustomNpc 真实实现）。
     */
    @Redirect(
            method = "render(Lnoppes/npcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            require = 1,
            remap = false
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void cnpcplus$hideSimpleCorpse(EntityRenderer<?> renderer, Entity entity, float entityYaw, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer, int packedLight) {
        if (this.cnpcplus$isCorpseHidden()) return;
        ((EntityRenderer) renderer).render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    @Redirect(
            method = "render(Lnoppes/npcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/client/renderer/RenderCustomNpc;renderNameTag(Lnoppes/npcs/entity/EntityNPCInterface;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V"),
            require = 1,
            remap = false
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void cnpcplus$hideSimpleCorpseNameTag(RenderCustomNpc<?, ?> self, EntityNPCInterface npcArg, Component text, PoseStack matrixStack, MultiBufferSource buffer, int packedLight, float partialTick) {
        if (this.cnpcplus$isCorpseHidden()) return;
        // renderNameTag 声明在父类 RenderNPCInterface：@Shadow 指向目标类会失配、
        // 整份 mixin 被静默丢弃（238b 教训）——改走接收者强转原始类型直调。
        ((RenderNPCInterface) self).renderNameTag(npcArg, text, matrixStack, buffer, packedLight, partialTick);
    }

    /** 与渲染门同源判据 + 延迟 store。隐藏从 deathTime >= delay 起（与渲染门 ModifyConstant 同边界）。 */
    @Unique
    private boolean cnpcplus$isCorpseHidden() {
        EntityNPCInterface npc = this.npc;
        if (npc == null) return false;
        if (!npc.isKilled()) return false;
        if (npc.stats == null || !npc.stats.hideKilledBody) return false;
        return npc.deathTime >= HideDelayStore.get(npc.stats);
    }
}
