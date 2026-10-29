package top.cnpcplus.mixin;

import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.stats.HideDelayStore;

/**
 * 238-2：渲染门隐藏延迟可配置。
 *
 * <h3>原版行为</h3>
 * {@code RenderNPCInterface.render:131}：{@code !isKilled || !hideKilledBody || deathTime <= 20}
 * 才渲染 —— 尸体固定 21 tick 后消失（字节码实证 render 内 {@code bipush 20} 两处：
 * offset33 渲染门、offset261 bossbar 条件，后者 {@code !isKilled} 短路与延迟无关不动）。
 *
 * <h3>修法</h3>
 * @Redirect 渲染门那次 {@code getfield deathTime}（ordinal=0，字节码实证该方法内唯一用于
 * 渲染门的读取），返回虚拟 deathTime 使原比较语义变为「{@code deathTime < delay} 才渲染」：
 * 真实值在窗口内返回 0（{@code <= 20} 恒真），窗口外返回 21（必早退）。
 * 与压缩门（MixinEntityCustomNpcDimensions）、simpleRender 门（#1）同一边界：
 * 隐藏从 {@code deathTime >= delay} 起。
 *
 * <p>{@code f_20919_} 是 {@code deathTime} 的生产 SRG 名（本 mixin remap=false 直写，
 * 先例 MixinRenderCustomNpcNameTag 的 {@code m_237119_}）；handler 体内的
 * {@code npc.deathTime} 由 reobf 重映射（先例 MixinEntityNPCKilledBody:127）。
 *
 * <p>{@code require = 1}：核心渲染门，注入失败必须崩出来而不是静默失效。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = RenderNPCInterface.class, remap = false)
public class MixinRenderNPCInterfaceHideDelay {

    @Redirect(
            method = "render(Lnoppes/npcs/entity/EntityNPCInterface;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "FIELD",
                    target = "Lnoppes/npcs/entity/EntityNPCInterface;f_20919_:I",
                    ordinal = 0),
            require = 1,
            remap = false
    )
    private int cnpcplus$hideDelayDeathTime(EntityNPCInterface npc) {
        return npc.deathTime < HideDelayStore.get(npc.stats) ? 0 : 21;
    }
}
