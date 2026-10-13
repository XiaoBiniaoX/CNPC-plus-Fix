package bin.cnpcplus.mixin.util;

import noppes.npcs.CustomNpcs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

/**
 * 缓存世界存档目录，防止关服时异步任务写到错误路径。
 *
 * <p>CNPC getLevelSaveDirectory 在异步任务执行时才解析，关服流程中 Server 已置 null
 * 时回落 new File(".")，文件写到进程工作目录而非世界目录。此处 Server 可用时缓存路径。
 */
@Mixin(value = CustomNpcs.class, remap = false)
public class MixinCustomNpcsDirCache {

    @Shadow(remap = false)
    public static net.minecraft.server.MinecraftServer Server;

    @Unique
    private static volatile File cnpcplus$cachedWorldDir;

    @Inject(method = "getLevelSaveDirectory(Ljava/lang/String;)Ljava/io/File;", at = @At("HEAD"), remap = false, cancellable = true)
    private static void cnpcplus$useCachedDir(String s, CallbackInfoReturnable<File> cir) {
        if (Server != null) {
            try {
                cnpcplus$cachedWorldDir = Server.getWorldPath(new net.minecraft.world.level.storage.LevelResource("customnpcs")).toFile();
            } catch (Exception ignored) {
            }
            return;
        }
        // Server 不可用（关服流程）：用缓存
        File base = cnpcplus$cachedWorldDir;
        if (base == null) return;
        File dir = (s != null) ? new File(base, s) : base;
        if (!dir.exists()) dir.mkdirs();
        cir.setReturnValue(dir);
    }
}
