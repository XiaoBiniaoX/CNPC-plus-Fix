package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.gui.FontRenderer;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NPC 名字铭牌渲染限制（客户端，阶段 34）。
 *
 * 原版路径（javap 实测 RenderNPCInterface）：
 * renderName 用 getDistanceSq &gt; 512.0（≈22.6 格）做外层门控；
 * renderLivingLabel 每个范围内 NPC 每帧执行：
 *   1) 标题宽度 getStringWidth + drawString（无条件，只要 title 非空）
 *   2) 名字影子 getStringWidth + drawString（仅 4 格内）
 *   3) 名字主体 getStringWidth + drawString
 * 每次 drawString = 一次字体贴图绑定 + 一次 tessellator flush。
 *
 * 本混入提供两个可配置距离限制（cfg performance 段，0 可分别关掉）：
 * nameRenderDistance  替换硬编码 512.0 平方距离门控（默认 16 格）
 * nameTitleDistance   只在该距离内画 &lt;标题&gt; 行（默认 12 格，0 = 不画标题）
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public abstract class MixinRenderNPCInterfaceName {

    @Unique
    private boolean cnpcplus$skipTitle;

    @Unique
    private int cnpcplus$step;

    @Inject(method = "renderLivingLabel(Lnoppes/npcs/entity/EntityNPCInterface;FFFILjava/lang/String;Ljava/lang/String;)V",
            at = @At("HEAD"))
    private void cnpcplus$trackLabel(EntityNPCInterface npc, float x, float y, float z,
                                     int renderDist, String name, String title, CallbackInfo ci) {
        this.cnpcplus$step = 0;
        double limit = CnpcPlusConfig.getNameTitleDistance();
        double distSq = (double) x * x + (double) y * y + (double) z * z;
        this.cnpcplus$skipTitle = title != null && !title.isEmpty()
                && (limit <= 0.0D || distSq > limit * limit);
    }

    @Redirect(method = "renderLivingLabel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/FontRenderer;func_78256_a(Ljava/lang/String;)I"),
            remap = false)
    private int cnpcplus$width(FontRenderer fontRenderer, String s) {
        if (this.cnpcplus$skipTitle && this.cnpcplus$step == 0) {
            this.cnpcplus$step = 1;
            return 0;
        }
        return fontRenderer.getStringWidth(s);
    }

    @Redirect(method = "renderLivingLabel",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/FontRenderer;func_78276_b(Ljava/lang/String;III)I"),
            remap = false)
    private int cnpcplus$draw(FontRenderer fontRenderer, String s, int x, int y, int color) {
        if (this.cnpcplus$skipTitle && this.cnpcplus$step == 1) {
            this.cnpcplus$step = 2;
            return 0;
        }
        return fontRenderer.drawString(s, x, y, color);
    }

    @ModifyConstant(method = "renderName",
            constant = @Constant(doubleValue = 512.0D),
            remap = false)
    private double cnpcplus$nameDistSq() {
        double limit = CnpcPlusConfig.getNameRenderDistance();
        return limit <= 0.0D ? 512.0D : limit * limit;
    }
}
