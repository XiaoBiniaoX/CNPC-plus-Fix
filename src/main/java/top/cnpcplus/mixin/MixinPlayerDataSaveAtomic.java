package top.cnpcplus.mixin;

import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.CustomNpcs;
import noppes.npcs.shared.common.util.LogWriter;
import noppes.npcs.util.NBTJsonUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 阶段3（问题2 P1-A/P1-B）：PlayerData 落盘原子化 + 备份。
 * 原版 lambda$save$1：写 _new → delete(real)（返回值丢弃）→ renameTo(real)（返回值丢弃）。
 * delete 成功而 rename 失败 → 文件不存在 → 空 tag 读入 → 任务+传送点全清。
 * 改为：写 _new → 备份 real 为 .bak → Files.move(REPLACE_EXISTING[, ATOMIC_MOVE])。
 * 失败保留旧文件，不再先 delete。签名与 javap 实证一致 lambda$save$1(String, CompoundTag)。
 */
@Mixin(value = noppes.npcs.controllers.data.PlayerData.class, remap = false)
public abstract class MixinPlayerDataSaveAtomic {

    @Inject(method = "lambda$save$1", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$atomicWrite(String filename, CompoundTag compound, CallbackInfo ci) {
        try {
            File saveDir = CustomNpcs.getLevelSaveDirectory("playerdata");
            File tmp = new File(saveDir, filename + "_new");
            File real = new File(saveDir, filename);
            if (real.exists()) {
                File bak = new File(saveDir, filename + ".bak");
                Files.copy(real.toPath(), bak.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            NBTJsonUtil.SaveFile(tmp, compound);
            try {
                Files.move(tmp.toPath(), real.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), real.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LogWriter.except(e);
        }
        ci.cancel();
    }
}
