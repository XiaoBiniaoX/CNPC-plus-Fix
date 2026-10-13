package bin.cnpcplus.mixin.recipe;

import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Relabel official ignoreDamage / ignoreNBT toggles to 1.20.1 cnpcplus meanings
 * and enforce mutual exclusion: opening 上方「模糊化处理」auto-closes 下方「仅名字检查」.
 * Uses language keys gui.ignoreDamage / gui.ignoreNBT overridden in cnpcplus lang.
 */
@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipesLabels {

    @Shadow(remap = false)
    private ContainerManageRecipes container;

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void cnpcplusRelabel(CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        // buttons already use gui.ignoreDamage / gui.ignoreNBT keys;
        // language override handles display. Force refresh messages.
        // 官方 YesNo 按钮宽 50 太宽，收窄到 30；并强制刷新为「模糊化处理/仅名字检查」文案。
        try {
            GuiButtonNop d = self.getButton(5);
            if (d != null) {
                d.setWidth(30);
                d.setMessage(net.minecraft.network.chat.Component.translatable("gui.ignoreDamage"));
            }
            GuiButtonNop n = self.getButton(6);
            if (n != null) {
                n.setWidth(30);
                n.setMessage(net.minecraft.network.chat.Component.translatable("gui.ignoreNBT"));
            }
        } catch (Throwable ignored) {
        }
    }

    /** 官方 buttonEvent 处理完 id 5/6 后补互斥：模糊化处理开启时仅名字检查自动关闭。 */
    @Inject(method = "buttonEvent", at = @At("RETURN"), remap = false)
    private void cnpcplus$exclusive(GuiButtonNop button, CallbackInfo ci) {
        if (button == null || (button.id != 5 && button.id != 6)) {
            return;
        }
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        try {
            if (this.container == null || this.container.recipe == null) {
                return;
            }
            if (button.id == 5 && this.container.recipe.ignoreDamage && this.container.recipe.ignoreNBT) {
                this.container.recipe.ignoreNBT = false;
                GuiButtonNop n = self.getButton(6);
                if (n != null) {
                    n.setDisplay(0);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
