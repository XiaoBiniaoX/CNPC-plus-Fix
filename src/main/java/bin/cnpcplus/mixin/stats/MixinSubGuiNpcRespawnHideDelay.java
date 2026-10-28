package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.IDataStatsHideDelayAccess;
import bin.cnpcplus.fx.FxStore;
import bin.cnpcplus.fx.client.FxEditor;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.SubGuiNpcRespawn;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.entity.data.DataStats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 238-2：重生界面加「尸体隐藏延迟」输入框。
 * 只有「隐藏尸体」开启时可用，与 1.20.1 行为一致。
 */
@Mixin(value = SubGuiNpcRespawn.class, remap = false)
public abstract class MixinSubGuiNpcRespawnHideDelay {

    @Shadow(remap = false)
    private DataStats stats;

    @Unique
    private static final int CNPCPLUS_TF_DELAY = 8;

    @Unique
    private static final int CNPCPLUS_LABEL_DELAY = 9;

    @Unique
    private static final int CNPCPLUS_BTN_DEATH_FX = 10;

    @Unique
    private static final int CNPCPLUS_LABEL_DEATH_FX = 11;

    @Unique
    private GuiNpcTextField cnpcplus$delayField;

    @Inject(method = "func_73866_w_", at = @At("TAIL"))
    private void cnpcplus$addHideDelay(CallbackInfo ci) {
        SubGuiNpcRespawn self = (SubGuiNpcRespawn) (Object) this;
        boolean enabled = this.stats.hideKilledBody;

        GuiNpcLabel label = new GuiNpcLabel(CNPCPLUS_LABEL_DELAY, "stats.hidedelay",
                self.guiLeft + 5, self.guiTop + 104);
        label.enabled = enabled;
        self.addLabel(label);

        this.cnpcplus$delayField = new GuiNpcTextField(CNPCPLUS_TF_DELAY, self, self.getFontRenderer(),
                self.guiLeft + 122, self.guiTop + 100, 50, 18,
                String.valueOf(((IDataStatsHideDelayAccess) this.stats).cnpcplus$getHideDelay()));
        this.cnpcplus$delayField.numbersOnly = true;
        this.cnpcplus$delayField.setMinMaxDefault(0, 200, 5);
        this.cnpcplus$delayField.enabled = enabled;
        self.addTextField(this.cnpcplus$delayField);

        self.addLabel(new GuiNpcLabel(CNPCPLUS_LABEL_DEATH_FX, "cnpcplus.deathfx",
                self.guiLeft + 5, self.guiTop + 140));
        self.addButton(new GuiNpcButton(CNPCPLUS_BTN_DEATH_FX,
                self.guiLeft + 122, self.guiTop + 135, 80, 20,
                "selectServer.edit"));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$editDeathParticles(GuiButton button, CallbackInfo ci) {
        if (button.id != CNPCPLUS_BTN_DEATH_FX) return;
        ((SubGuiNpcRespawn) (Object) this).setSubGui(new FxEditor(
                list -> FxStore.put(this.stats, list), FxStore.get(this.stats)));
        ci.cancel();
    }

    @Inject(method = "func_146284_a", at = @At("TAIL"))
    private void cnpcplus$refreshHideDelayState(GuiButton button, CallbackInfo ci) {
        if (this.cnpcplus$delayField == null) return;
        boolean enabled = this.stats.hideKilledBody;
        this.cnpcplus$delayField.enabled = enabled;
        GuiNpcLabel label = ((SubGuiNpcRespawn) (Object) this).getLabel(CNPCPLUS_LABEL_DELAY);
        if (label != null) {
            label.enabled = enabled;
        }
    }

    @Inject(method = "unFocused", at = @At("TAIL"), require = 1)
    private void cnpcplus$saveHideDelay(GuiNpcTextField textfield, CallbackInfo ci) {
        if (textfield == this.cnpcplus$delayField) {
            ((IDataStatsHideDelayAccess) this.stats).cnpcplus$setHideDelay(textfield.getInteger());
        }
    }
}
