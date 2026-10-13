package bin.cnpcplus.mixin.recipe;

import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 合成表两个开关的标签改名（对标商人界面）：
 * 上方「模糊化处理」（cnpcplus.recipe.mode.fuzzy，含 recipeFuzzyMatchRules 自带配置）
 * 下方「仅名字检查」（cnpcplus.recipe.mode.nameOnly）
 * 代码级覆盖而非 lang 覆盖：gui.ignoreDamage/gui.ignoreNBT 是合成表/商人/任务三处共用键，
 * 且同域 lang 覆盖依赖资源包加载顺序，不保证生效。
 * Yes/No 按钮（id 5/6）原宽 50（114..164）压到标签文字上：改为 36（114..150，
 * 对标 1.20.1 @ModifyArg 宽度 36，用户 2026-10-13 反馈"宽度太大了"）。
 */
@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipesLabels {

    @Inject(method = "func_73866_w_", at = @At("RETURN"), remap = false)
    private void cnpcplusRelabel(CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        try {
            GuiNpcLabel fuzzy = self.getLabel(0);
            if (fuzzy != null) {
                fuzzy.label = cnpcplusModeLabel("cnpcplus.recipe.mode.fuzzy", "Fuzzy Matching");
            }
            GuiNpcLabel nameOnly = self.getLabel(1);
            if (nameOnly != null) {
                nameOnly.label = cnpcplusModeLabel("cnpcplus.recipe.mode.nameOnly", "Name Only");
            }
            GuiNpcButton fuzzyBtn = self.getButton(5);
            if (fuzzyBtn != null) {
                fuzzyBtn.width = 36;
            }
            GuiNpcButton nameOnlyBtn = self.getButton(6);
            if (nameOnlyBtn != null) {
                nameOnlyBtn.width = 36;
            }
        } catch (Throwable t) {
            bin.cnpcplus.CnpcPlus.LOGGER.error("[GUI] recipe label relabel failed", t);
        }
    }

    private static String cnpcplusModeLabel(String key, String fallback) {
        try {
            String s = I18n.translateToLocal(key);
            if (s == null || s.isEmpty() || s.equals(key)) return fallback;
            return s;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
