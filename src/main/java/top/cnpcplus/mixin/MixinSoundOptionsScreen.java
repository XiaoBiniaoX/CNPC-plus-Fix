package top.cnpcplus.mixin;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.config.CnpcPlusConfigData;

import java.util.Arrays;

/**
 * 在「音乐与声音」界面里加一条吟游诗人专属音量滑条。
 *
 * <p>为什么注入 getAllSoundOptionsExceptMaster 而不是 init：
 * 原版 init 的装配顺序是「主音量(独占一行) → 分类音量(两列小条) → 字幕/音量提示 → 音效设备」。
 * 早期实现在 init 的 TAIL 里调 OptionsList.addBig 追加，滑条确实进了列表，
 * 但落在所有原版条目之后，玩家必须把列表滚到最底部才能找到。
 * getAllSoundOptionsExceptMaster 返回的正是「分类音量」那一组，
 * 在它的返回值里追加，滑条就与音乐、唱片机等排在同一区块，进界面即可见，无需滚动。
 *
 * <p>注意 @Shadow 不进 refmap（开发 mojmap ↔ 生产 SRG 对不上会让整个 mixin 被静默丢弃），
 * 所以这里刻意不用 @Shadow，改为纯 @Inject 注入返回值，注入目标由 refmap 正常映射。
 */
@Mixin(value = SoundOptionsScreen.class, remap = true)
public class MixinSoundOptionsScreen {

    @Inject(method = "getAllSoundOptionsExceptMaster", at = @At("RETURN"), cancellable = true)
    private void cnpcplus$addBardSlider(CallbackInfoReturnable<OptionInstance<?>[]> cir) {
        OptionInstance<?>[] vanilla = cir.getReturnValue();
        if (vanilla == null) return;

        OptionInstance<Double> bard = new OptionInstance<>(
                "options.cnpcplus.bardVolume",
                OptionInstance.noTooltip(),
                // 滑条上显示成百分比，跟原版各音量条的观感一致
                (component, value) -> component.copy().append(Component.literal(": " + Math.round(value * 100) + "%")),
                OptionInstance.UnitDouble.INSTANCE,
                CnpcPlusConfigData.BardVolume.get(),
                // 拖动即写回配置；MixinSoundEngine 每 tick 读它刷新正在播放的通道，所以是实时生效
                value -> CnpcPlusConfigData.BardVolume.set(value));

        OptionInstance<?>[] merged = Arrays.copyOf(vanilla, vanilla.length + 1);
        merged[vanilla.length] = bard;
        cir.setReturnValue(merged);
    }
}
