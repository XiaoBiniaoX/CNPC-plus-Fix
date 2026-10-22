package top.cnpcplus.mixin;

import java.nio.file.Path;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.transport.client.TransportDropHandler;

/**
 * 任务241j：Screen.onFilesDrop 转发（源码 mojmap 名，经 refmap 映射到运行名）。
 * 传送师编辑器是子页，本身收不到 vanilla 的文件拖放事件。
 */
@Mixin(value = Screen.class, remap = true)
public abstract class MixinScreenFilesDrop {

    @Inject(method = "onFilesDrop", at = @At("HEAD"))
    private void cnpcplus$forward(List<Path> paths, CallbackInfo ci) {
        TransportDropHandler.accept(paths);
    }
}
