package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.ServerCloneController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.function.Function;

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
 * <p>修法：@Redirect 把 computeIfAbsent 整个换成 put —— 无条件覆盖缓存条目。
 * 原版的落盘逻辑（写 _new 临时文件 → 删旧 → rename）完全保留，只补正缓存。
 *
 * <p>兼容性：只影响内存缓存，不改磁盘格式、不改文件名规则、不改 NBT 结构，
 * 旧存档与旧 clones 目录完全兼容。服务端专有类，无客户端引用。
 */
@Mixin(value = ServerCloneController.class, remap = false)
public class MixinServerCloneControllerOverwrite {

    @Shadow(remap = false)
    private Map<String, CompoundTag> cloneCache;

    /**
     * 把 saveClone 里的 computeIfAbsent 换成 put。
     * saveClone 只有一处 computeIfAbsent（getCloneData 里那处是加载路径，不该动），ordinal=0。
     */
    @Redirect(method = "saveClone",
              at = @At(value = "INVOKE",
                       target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object cnpcplus$replaceComputeIfAbsent(Map<String, CompoundTag> map,
                                                   Object key,
                                                   Function<Object, CompoundTag> mappingFunction) {
        // 无条件覆盖：先算出新值再 put。
        CompoundTag value = mappingFunction.apply(key);
        if (value != null) {
            map.put((String) key, value);
        }
        return value;
    }
}
