package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.IRangedSpinAccess;
import bin.cnpcplus.common.ProjectileSpinKeys;
import bin.cnpcplus.melee.RangedBuffStore;
import bin.cnpcplus.melee.SubGuiRangedBuffs;
import bin.cnpcplus.fx.FxStore;
import bin.cnpcplus.fx.client.FxEditor;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.config.GuiUtils;
import noppes.npcs.client.gui.SubGuiNpcProjectiles;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.entity.data.DataRanged;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;

/** 远程效果入口与近战保持一致；3D 投射物提供三轴小数自旋和悬停说明。 */
@Mixin(value = SubGuiNpcProjectiles.class, remap = false)
public abstract class MixinSubGuiNpcProjectilesSpin extends SubGuiInterface {
    @Shadow(remap = false) private DataRanged stats;

    @Inject(method = "func_73866_w_()V", at = @At("TAIL"), require = 1)
    private void cnpcplus$projectileExtras(CallbackInfo ci) {
        GuiNpcButton effects = this.getButton(4);
        effects.width = 140;
        effects.setDisplayText("cnpcplus.ranged.buffs");
        GuiNpcTextField legacyTime = this.getTextField(5);
        if (legacyTime != null) {
            legacyTime.enabled = false;
            legacyTime.canEdit = false;
            legacyTime.setVisible(false);
        }
        GuiNpcButton legacyAmp = this.getButton(10);
        if (legacyAmp != null) legacyAmp.visible = false;
        this.getButton(5).setDisplayText("cnpcplus.fx.edit");
        if (!this.stats.getRender3D()) return;
        this.getLabel(10).x = this.guiLeft + 110;
        this.getButton(8).x = this.guiLeft + 154;
        this.getButton(8).width = 35;
        this.getLabel(11).x = this.guiLeft + 195;
        this.getLabel(11).y = this.guiTop + 45;
        this.getButton(9).x = this.guiLeft + 225;
        this.getButton(9).y = this.guiTop + 40;
        this.getButton(9).width = 25;
        // 三轴小数自旋只对开启自旋的 3D 投射物有意义；关掉时连输入框都不该出现。
        if (!this.stats.getSpins()) return;
        for (int axis = 0; axis < 3; axis++) {
            int x = this.guiLeft + 110 + axis * 48;
            this.addLabel(new GuiNpcLabel(12 + axis, "XYZ".substring(axis, axis + 1), x, this.guiTop + 63));
            GuiNpcTextField field = new GuiNpcTextField(12 + axis, this, this.getFontRenderer(),
                    x, this.guiTop + 76, 42, 18,
                    Float.toString(((IRangedSpinAccess) this.stats).cnpcplus$getSpinSpeed(axis)));
            field.setMaxStringLength(12);
            this.addTextField(field);
        }
    }

    @Inject(method = "unFocused", at = @At("TAIL"), require = 1)
    private void cnpcplus$saveSpinSpeed(GuiNpcTextField field, CallbackInfo ci) {
        if (field == null) return;
        for (int axis = 0; axis < 3; axis++) {
            if (this.getTextField(12 + axis) != field) continue;
            float value = 0;
            try { value = ProjectileSpinKeys.clamp(Float.parseFloat(field.getText().trim())); }
            catch (NumberFormatException ignored) { }
            ((IRangedSpinAccess) this.stats).cnpcplus$setSpinSpeed(axis, value);
            field.setText(Float.toString(value));
        }
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$openRangedEffects(GuiButton button, CallbackInfo ci) {
        if (button.id == 5) {
            this.setSubGui(new FxEditor(list -> FxStore.put(this.stats, list), FxStore.get(this.stats)));
            ((GuiNpcButton) button).setDisplayText("cnpcplus.fx.edit");
            ci.cancel();
            return;
        }
        if (button.id != 4) return;
        RangedBuffStore.migrateLegacy(this.stats);
        this.setSubGui(new SubGuiRangedBuffs(this.stats));
        ((GuiNpcButton) button).setDisplayText("cnpcplus.ranged.buffs");
        ci.cancel();
    }

    /** 按钮 8 是自旋开关：按下后立即重建 GUI，让三轴输入框当帧显现/隐藏。 */
    @Inject(method = "func_146284_a", at = @At("TAIL"), require = 1)
    private void cnpcplus$refreshSpinFields(GuiButton button, CallbackInfo ci) {
        if (button.id == 8) this.initGui();
    }

    @Override
    public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        super.func_73863_a(mouseX, mouseY, partialTicks);
        if (!this.hasSubGui() && this.stats.getRender3D() && this.stats.getSpins()
                && mouseX >= this.guiLeft + 108 && mouseX <= this.guiLeft + 250
                && mouseY >= this.guiTop + 60 && mouseY <= this.guiTop + 95) {
            GuiUtils.drawHoveringText(Arrays.asList(I18n.format("cnpcplus.spin.tooltip").split("\\n")),
                    mouseX, mouseY, this.width, this.height, 220, this.getFontRenderer());
        }
    }
}
