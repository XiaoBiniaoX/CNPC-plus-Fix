package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.LinkedNpcController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.linked.LinkedSyncFlags;

/**
 * 同步标签（LinkedData）的两处修复：脚本同步开关的持久化，以及 {@code time} 的持久化。
 *
 * <h3>修复一：time 从不落盘（「重进游戏改动回弹」的根因）</h3>
 *
 * <p>原版 {@code setNBT}/{@code getNBT} 只读写 {@code LinkedName} 与 {@code NPCData}，
 * **完全不碰 {@code time}**（javap 实证：setNBT 只有两个 putfield、getNBT 只有
 * putString + put；磁盘上的 New.json 顶层也确实只有这两个键）。
 *
 * <p>而 {@code time} 是整套同步的唯一传播信号：{@code EntityNPCInterface.aiStep}
 * 每 tick 比较 {@code linkedData.time > npc.linkedLast}，命中就调 {@code loadNpcData}
 * 把共享数据合并回 NPC（{@code NBTMerge(readNpcData(npc), data.data)}，
 * 共享数据是覆盖方，永远赢）。
 *
 * <p>于是重启后必然回弹：{@code loadNpcs()} 对每个 json 走 {@code new LinkedData()}，
 * 构造函数把 {@code time} 设成**当前时刻**，随后 {@code setNBT} 不会修正它；
 * 而 NPC 的 {@code linkedLast} 也没被持久化（{@code m_7380_} 只写 {@code LinkedNpcName}），
 * 重启后恒为 0。{@code 当前时刻 > 0} 恒真 → 第一 tick 就用磁盘上的旧共享数据覆盖 NPC。
 *
 * <p>修法：把 {@code time} 写进 NBT 并读回。读不到时**回退 0** 而不是保留构造函数塞的
 * 当前时刻 —— 这样「旧存档首次升级」时不会被误判为「有更新」而触发一次全量覆盖。
 *
 * <h3>修复二：脚本同步开关的存储 key</h3>
 *
 * <p>原来用 {@code ExtraDataStorage}（WeakHashMap + 实例 identity hash）存这个布尔值。
 * 但 {@code loadNpcs()} 每次重载都 {@code new LinkedData()}，旧实例被回收、状态随之丢失；
 * 且 {@code saveNpcData} 走的是 {@code npc.linkedData}，与 {@code setNBT} 时的实例
 * 不一定是同一个对象，读出来就是默认 false。
 *
 * <p>改用 {@link LinkedSyncFlags}，以标签名（String，equals/hashCode 稳定）作 key。
 *
 * <p>兼容性：两个新键都只在有值时写入；旧存档读不到键就用默认值，行为与原版一致。
 */
@Mixin(value = LinkedNpcController.LinkedData.class, remap = false)
public class MixinLinkedData {

    @Shadow(remap = false)
    public String name;

    @Shadow(remap = false)
    public CompoundTag data;

    @Shadow(remap = false)
    public long time;

    @Inject(method = "setNBT", at = @At("TAIL"), remap = false)
    private void cnpcplus$readExtras(CompoundTag compound, CallbackInfo ci) {
        LinkedSyncFlags.setSyncScripts(this.name, compound.getBoolean("SyncScripts"));

        // 读回上次落盘的 time。读不到就归 0：构造函数塞的是「当前时刻」，
        // 留着它会让每次重启都被判定为「共享数据有更新」，从而拿旧数据覆盖 NPC。
        this.time = compound.contains("Time", 99) ? compound.getLong("Time") : 0L;
    }

    @Inject(method = "getNBT", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeExtras(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag out = cir.getReturnValue();
        if (out == null) return;
        if (LinkedSyncFlags.isSyncScripts(this.name)) {
            out.putBoolean("SyncScripts", true);
        }
        // 落盘 time，让「谁更新」这个判断在重启后依然成立。
        out.putLong("Time", this.time);
    }
}
