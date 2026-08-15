package bin.cnpcplus.mixin;

import bin.cnpcplus.config.CnpcPlusConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NPC 名字遮挡。
 *
 * 实现方式：可见性剔除，而不是修改字体深度模式。
 *
 * 背景（诊断日志实证）：CNPC 的 renderLivingLabel 每帧参数完全恒定
 * （light/背景色/颜色/坐标不变，仅名字两遍绘制）。但只要让文字参与深度测试
 * （Font.DisplayMode.NORMAL 或 POLYGON_OFFSET），就会出现随视角转动部分字符缺失。
 * 三次深度模式方案均失败，故改为离散判定：
 *
 * - 玩家与 NPC 之间有方块阻挡 → 取消整段名字绘制。
 * - 无阻挡 → 完全保持 CNPC 原版绘制，不改任何参数。
 *
 * 这样不存在深度精度竞争，字符不可能缺失。
 * ponytail: hasLineOfSight 为方块级射线，不含实体遮挡；若需实体遮挡再单独扩展。
 */
@Mixin(RenderNPCInterface.class)
public class RenderNPCInterfaceMixin {

    @Inject(method = "renderLivingLabel", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$hideWhenBlocked(EntityNPCInterface npc, PoseStack matrixStack, MultiBufferSource buffer,
                                          int light, CallbackInfo ci) {
        if (!CnpcPlusConfig.NPC_NAMES_OBSCURED.get()) return;
        Player player = Minecraft.getInstance().player;
        if (player == null || npc == null) return;
        if (!player.hasLineOfSight(npc)) {
            ci.cancel();
        }
    }

    @Redirect(method = "renderLivingLabel", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"), remap = false)
    private MutableComponent cnpcplus$titleColor(String key) {
        if (key.indexOf('&') >= 0) {
            return Component.literal(key.replace('&', '\u00a7'));
        }
        return Component.translatable(key);
    }
}
