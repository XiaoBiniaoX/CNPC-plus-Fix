package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * 在「音乐与声音」界面里加一条吟游诗人专属音量滑条。
 *
 * <p>为什么注入 getAllSoundOptionsExceptMaster 而不是 addOptions：
 * 原版 addOptions 的装配顺序是「主音量(addBig) → 分类音量(addSmall 两列) → 音效设备(addBig) → 字幕/定向音频」。
 * 早期实现在 addOptions 的 TAIL 里调 OptionsList.addBig 追加，滑条确实进了列表，
 * 但落在所有原版条目之后，玩家必须把列表滚到最底部才能看到，表现就像滑条消失了。
 * getAllSoundOptionsExceptMaster 返回的正是「分类音量」那一组，
 * 在它的返回值里追加，滑条就与音乐、唱片机等排在同一区块，进界面即可见，无需滚动。
 *
 * <p>remap 固定 false：这是 1.21.1 NeoForge 与 1.20.1 Forge 的关键差异。
 * NeoForge 运行时直接使用 mojmap，不存在 SRG 中间名，本库全部混入均为 remap=false；
 * 照搬 1.20.1 的 remap=true 会让注解处理器去找不存在的混淆映射并直接编译失败。
 */
@Mixin(value = SoundOptionsScreen.class, remap = false)
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
                CnpcPlusConfig.BARD_VOLUME.get(),
                // 拖动即写回配置；MixinSoundEngine 每 tick 读它刷新正在播放的通道，所以是实时生效
                value -> CnpcPlusConfig.BARD_VOLUME.set(value));

        OptionInstance<?>[] merged = Arrays.copyOf(vanilla, vanilla.length + 1);
        merged[vanilla.length] = bard;
        cir.setReturnValue(merged);
    }
}
