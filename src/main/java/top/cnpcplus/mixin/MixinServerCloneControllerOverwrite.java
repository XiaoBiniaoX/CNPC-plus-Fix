package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.ServerCloneController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * 修复复制魔杖「保存同名克隆时不覆盖」。
 *
 * <p>根因（原版源码实证 ServerCloneController.saveClone 末尾）：
 * <pre>this.cloneCache.computeIfAbsent(tab + "|" + name, k -&gt; compound.copy());</pre>
 * computeIfAbsent 在 key 已存在时是 no-op。而 getCloneData 的第一件事就是查这个缓存：
 * <pre>if (this.cloneCache.containsKey(tab + "|" + name)) return this.cloneCache.get(...).copy();</pre>
 * 于是覆盖保存后，磁盘上的 json 是新的，内存缓存却仍是旧 NBT，本次服务器会话内所有
 * 生成/读取都拿到旧数据 —— 玩家看到的就是「覆盖没生效」。
 *
 * <p>为什么表现为「有时覆盖有时不覆盖」：缓存只有在该克隆**被读过或存过一次**之后才有条目。
 * 首次保存一个新名字时 computeIfAbsent 正常写入，看起来是好的；之后再覆盖同名就失效。
 * 而 removeClone 会 `cloneCache.remove(...)`，所以「先删掉再保存」永远正常 ——
 * 这条不对称正是偶发感的来源。
 *
 * <p>修法：保存完成后强制用 put 覆盖缓存条目。注入 RETURN 而非改写方法体，
 * 原版的落盘逻辑（写 _new 临时文件 → 删旧 → rename）完全保留，只补正缓存。
 *
 * <p>兼容性：只影响内存缓存，不改磁盘格式、不改文件名规则、不改 NBT 结构，
 * 旧存档与旧 clones 目录完全兼容。服务端专有类，无客户端引用。
 */
@Mixin(value = ServerCloneController.class, remap = false)
public class MixinServerCloneControllerOverwrite {

    @Shadow(remap = false)
    private Map<String, CompoundTag> cloneCache;

    @Inject(method = "saveClone", at = @At("RETURN"), remap = false)
    private void cnpcplus$forceCacheOverwrite(int tab, String name, CompoundTag compound, CallbackInfo ci) {
        if (this.cloneCache == null || name == null || compound == null) return;
        // 与原版一致的 key 格式，且与原版一样存副本（避免外部后续修改 compound 影响缓存）。
        this.cloneCache.put(tab + "|" + name, compound.copy());
    }
}
