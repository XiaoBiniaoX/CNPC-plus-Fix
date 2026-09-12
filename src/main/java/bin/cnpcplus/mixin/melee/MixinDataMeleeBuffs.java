package bin.cnpcplus.mixin.melee;

import bin.cnpcplus.melee.MeleeBuff;
import bin.cnpcplus.melee.MeleeBuffUtil;
import bin.cnpcplus.melee.MeleeBuffsAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 近战附加效果的多 BUFF 数据层。
 *
 * <p>原版只能配一个效果，且候选列表是硬编码的 1..32（SubGuiNpcMeleeProperties 的静态块），
 * 完全读不到其他 mod 注册的效果。这里新增一张 BUFF 表，用独立 NBT 键
 * {@code CNPCPlusMeleeBuffs}，原版的 {@code PotionEffect / PotionDuration / PotionAmp}
 * 三个键一个字节都不动 —— 所以旧存档、脚本 API（{@code getEffectType/setEffect}）
 * 全部照常可用，这就是「隐藏而非删除」。
 *
 * <p>与 1.12.2 实现对齐：条目主键是<b>注册名字符串</b>而不是数值 id，
 * 因为数值 id 随 mod 增删会串位。旧存档里原版的单效果（数值 id）在首次读取时
 * 被转成注册名迁移进表内第一条，玩家已配好的效果不丢。
 *
 * <p>写入时若表为空则显式移除该键，这样「清空了 BUFF」这件事能正确落盘，
 * 不会因为留着空列表而与「旧存档没有该键」混淆。
 */
@Mixin(value = DataMelee.class, remap = false)
public class MixinDataMeleeBuffs implements MeleeBuffsAccess {

    @Unique
    private final List<MeleeBuff> cnpcplus$buffs = new ArrayList<>();

    @Unique
    private static final String CNPCPLUS_KEY = "CNPCPlusMeleeBuffs";

    @Inject(method = "save", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveBuffs(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag out = cir.getReturnValue();
        if (out == null) return;

        if (this.cnpcplus$buffs.isEmpty()) {
            // 明确移除而不是留空列表：让「清空了 BUFF」能正确落盘。
            out.remove(CNPCPLUS_KEY);
            return;
        }

        ListTag list = new ListTag();
        for (MeleeBuff b : this.cnpcplus$buffs) {
            if (b == null || b.effectId == null || b.effectId.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putString("Effect", b.effectId);
            t.putInt("Amp", b.amp);
            t.putInt("Seconds", b.seconds);
            list.add(t);
        }
        out.put(CNPCPLUS_KEY, list);
    }

    @Inject(method = "load", at = @At("RETURN"), require = 1)
    private void cnpcplus$loadBuffs(CompoundTag compound, CallbackInfo ci) {
        this.cnpcplus$buffs.clear();
        if (compound == null) return;

        if (compound.contains(CNPCPLUS_KEY, Tag.TAG_LIST)) {
            ListTag list = compound.getList(CNPCPLUS_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size() && this.cnpcplus$buffs.size() < MeleeBuff.MAX_ENTRIES; i++) {
                CompoundTag t = list.getCompound(i);
                String name = t.getString("Effect");
                if (name == null || name.isEmpty()) continue;
                this.cnpcplus$buffs.add(new MeleeBuff(name, t.getInt("Amp"), t.getInt("Seconds")));
            }
            return;
        }

        // 旧存档迁移：把原版的单效果（数值 id）转成注册名搬进表内第一条。
        int oldType = compound.getInt("PotionEffect");
        if (oldType != 0) {
            String name = MeleeBuffUtil.nameOfNumeric(oldType);
            if (name != null) {
                int seconds = compound.getInt("PotionDuration");
                this.cnpcplus$buffs.add(new MeleeBuff(name, compound.getInt("PotionAmp"),
                        seconds <= 0 ? 5 : seconds));
            }
        }
    }

    @Override
    public List<MeleeBuff> cnpcplus$getMeleeBuffs() {
        return this.cnpcplus$buffs;
    }

    @Override
    public void cnpcplus$setMeleeBuffs(List<MeleeBuff> buffs) {
        this.cnpcplus$buffs.clear();
        if (buffs == null) return;
        for (MeleeBuff b : buffs) {
            if (b == null) continue;
            if (this.cnpcplus$buffs.size() >= MeleeBuff.MAX_ENTRIES) break;
            this.cnpcplus$buffs.add(b);
        }
    }
}
