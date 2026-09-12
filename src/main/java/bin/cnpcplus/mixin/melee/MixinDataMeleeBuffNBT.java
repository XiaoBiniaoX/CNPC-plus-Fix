package bin.cnpcplus.mixin.melee;

import bin.cnpcplus.melee.MeleeBuffStore;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把近战 BUFF 表挂进 {@code DataMelee} 的 NBT，白拿存档与两端同步。
 *
 * <h3>为什么不需要新增网络包</h3>
 * {@code DataStats.writeToNBT}（反编译 99 行）会调 {@code melee.writeToNBT(compound)}，
 * 而 GUI 保存走 {@code EnumPacketServer.MainmenuStatsSave} →
 * {@code PacketHandlerServer:515-516} 的 {@code npc.stats.readToNBT(...)} 全量覆盖，
 * 读取走 {@code MainmenuStatsGet} → {@code npc.stats.writeToNBT(...)}。
 * 所以只要 BUFF 表在 DataMelee 的复合标签里，它就自动：
 * <ul>
 *   <li>随 GUI 保存过河到服务端；</li>
 *   <li>随 GUI 打开从服务端回到客户端；</li>
 *   <li>随 NPC 实体 NBT 进存档。</li>
 * </ul>
 * 这与阶段 24 近战打击台词用的是同一条白拿通道。
 *
 * <h3>注入点选择</h3>
 * {@code writeToNBT} 返回 {@code NBTTagCompound}，所以 RETURN 处的 handler
 * 必须用 {@code CallbackInfoReturnable}（findings 阶段 23 崩溃3 与阶段 25 的教训），
 * 并且要从 {@code cir.getReturnValue()} 取那个真正被返回的标签 ——
 * 原版是 {@code return compound;}，两者同一个对象，但按规矩取返回值更稳。
 *
 * {@code readFromNBT} 返回 void，用 {@code CallbackInfo} 即可。
 */
@Mixin(value = DataMelee.class, remap = false)
public class MixinDataMeleeBuffNBT {

    @Inject(method = "readFromNBT", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$readBuffs(NBTTagCompound compound, CallbackInfo ci) {
        MeleeBuffStore.read((DataMelee) (Object) this, compound);
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$writeBuffs(NBTTagCompound compound,
                                     CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound out = cir.getReturnValue();
        MeleeBuffStore.write((DataMelee) (Object) this, out == null ? compound : out);
    }
}
