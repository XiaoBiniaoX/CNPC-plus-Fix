package top.cnpcplus.mixin;

import noppes.npcs.api.wrapper.WrapperNpcAPI;
import org.spongepowered.asm.mixin.*;
import top.cnpcplus.shader.*;

@Mixin(value = WrapperNpcAPI.class, remap = false)
public class MixinShaderApi {
    @Unique public ShaderManager getShaderManager() { return ShaderRuntime.manager(); }
    @Unique public ShaderController getShaderController() {
        if (!ShaderRuntime.client()) throw new IllegalStateException("Use player.getShaderController() on the server");
        return ShaderController.local();
    }
}
