package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.melee.MeleeBuffStore;

/**
 * 把近战 BUFF 表挂进 {@code DataMelee} 的 NBT，白拿存档与两端同步。
 *
 * <h3>为什么不需要新增网络包</h3>
 * {@code DataStats.save}（反编译 99-100 行）会调 {@code melee.save(compound)}，
 * 且是**直接展开到 DataStats 自己的顶层标签上**，没有子标签包裹；
 * {@code DataStats.readToNBT}（{@code :79-80}）对应地调 {@code melee.load(compound)}。
 *
 * <p>而 GUI 保存走 {@code SPacketMenuSave(EnumMenuType.STATS, stats.save(...))}
 * （{@code GuiNpcStats.java:175}）→ 服务端 {@code npc.stats.readToNBT(this.data)}
 * （{@code SPacketMenuSave.java:91-93}）；读取走 {@code SPacketMenuGet(STATS)}
 * （{@code GuiNpcStats.java:74}）。所以只要 BUFF 表在 DataMelee 的复合标签里，它就自动：
 * <ul>
 *   <li>随 GUI 保存过河到服务端；</li>
 *   <li>随 GUI 打开从服务端回到客户端；</li>
 *   <li>随 NPC 实体 NBT 进存档。</li>
 * </ul>
 *
 * <h3>注入点签名</h3>
 * {@code save} 返回 {@code CompoundTag}，所以 handler 必须用
 * {@code CallbackInfoReturnable}（用 {@code CallbackInfo} 会在运行期报签名错误，
 * 第十八轮踩过）。并且要从 {@code cir.getReturnValue()} 取真正被返回的那个标签 ——
 * 原版是 {@code return compound}，两者同一对象，但按规矩取返回值更稳。
 *
 * <p>{@code load} 返回 void，用 {@code CallbackInfo} 即可。
 */
@Mixin(value = DataMelee.class, remap = false)
public class MixinDataMeleeBuffNBT {

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private void cnpcplus$readBuffs(CompoundTag compound, CallbackInfo ci) {
        MeleeBuffStore.read((DataMelee) (Object) this, compound);
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$writeBuffs(CompoundTag compound,
                                     CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag out = cir.getReturnValue();
        MeleeBuffStore.write((DataMelee) (Object) this, out == null ? compound : out);
    }
}
