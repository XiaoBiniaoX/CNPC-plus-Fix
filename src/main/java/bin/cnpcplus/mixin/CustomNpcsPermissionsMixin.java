package bin.cnpcplus.mixin;

import noppes.npcs.CustomNpcsPermissions;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * CNPC 启动时在 registerNodes 里逐行 INFO 打印全部权限节点名（customnpcs.global.* 等），
 * 刷屏无用，直接吞掉 Logger.info；注册本身（event.addNodes）不受影响。
 */
@Mixin(value = CustomNpcsPermissions.class, remap = false)
public class CustomNpcsPermissionsMixin {

    @Redirect(method = "registerNodes", at = @At(value = "INVOKE", target = "Lorg/apache/logging/log4j/Logger;info(Ljava/lang/String;)V"))
    private void cnpcplus$silenceInfo(Logger logger, String message) {
        // suppress CNPC permission node log spam
    }
}
