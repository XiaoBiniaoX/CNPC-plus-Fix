package bin.cnpcplus.mixin.spawner;

import bin.cnpcplus.spawner.SpawnDelayStore;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.client.gui.roles.GuiNpcSpawner;
import noppes.npcs.roles.JobSpawner;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 召唤师界面补回「召唤间隔」输入框。
 *
 * <p>控件 id 沿用 1.20.1 原版：文本框 id=11、标签 id=12，这两个编号在 1.21.1 的
 * `GuiNpcSpawner.init` 里都没被占用（文本框只用了 7/8/9），可以直接复用而不冲突。
 * 坐标也照 1.20.1 摆在 X/Y/Z 偏移那一行的右侧空位。
 */
@Mixin(value = GuiNpcSpawner.class, remap = false)
public class MixinGuiNpcSpawnerDelay {

    @Shadow
    private JobSpawner job;

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$addDelayField(CallbackInfo ci) {
        GuiNpcSpawner self = (GuiNpcSpawner) (Object) this;
        if (this.job == null) return;

        // 与 Z 偏移框同一行：mixin 内拿不到原方法的局部变量 y，直接从已存在的控件反推。
        GuiTextFieldNop zField = self.getTextField(9);
        if (zField == null) return;
        int y = zField.getY();

        self.addLabel(new GuiLabel(12, "cnpcplus.spawner.delay", self.guiLeft + 200, y + 5));
        GuiTextFieldNop tf = new GuiTextFieldNop(
                11, (Screen) self, self.guiLeft + 290, y, 60, 20,
                "" + SpawnDelayStore.getDelay(this.job));
        self.addTextField(tf);
        tf.numbersOnly = true;
        // 单位与 1.20.1 原版一致，是 tick。
        tf.setMinMaxDefault(0, Integer.MAX_VALUE, 0);
    }

    @Inject(method = "unFocused", at = @At("HEAD"))
    private void cnpcplus$readDelayField(GuiTextFieldNop textfield, CallbackInfo ci) {
        if (textfield == null || textfield.id != 11) return;
        if (this.job == null) return;
        SpawnDelayStore.setDelay(this.job, textfield.getInteger());
    }

    /**
     * 保存前先把输入框当前值写进 store。
     *
     * <p>原版 `save()` 会调 `job.save(...)` 发 `SPacketNpcJobSave`，而 CD 值由
     * MixinJobSpawnerDelay 挂在 `save` 的 RETURN 上写入 NBT，所以只要 store 已更新，
     * 整条同步链无需新增数据包。这里补一次是防止玩家改完没失焦就直接关界面。
     */
    @Inject(method = "save", at = @At("HEAD"))
    private void cnpcplus$syncBeforeSave(CallbackInfo ci) {
        GuiNpcSpawner self = (GuiNpcSpawner) (Object) this;
        if (this.job == null) return;
        GuiTextFieldNop tf = self.getTextField(11);
        if (tf == null) return;
        SpawnDelayStore.setDelay(this.job, tf.getInteger());
    }
}
