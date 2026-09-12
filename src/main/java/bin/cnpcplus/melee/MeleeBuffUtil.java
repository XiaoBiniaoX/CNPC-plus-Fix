package bin.cnpcplus.melee;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 药水效果注册表的读写工具。
 *
 * <p>候选列表直接来自 {@code BuiltInRegistries.MOB_EFFECT}，所以自动包含全部 mod
 * 注册的效果，不再受原版界面里硬编码 1..32 的限制。
 * 写法与 CNPC 自己的 {@code GuiNpcHealer} 一致，主键用注册名字符串而不是数值 id。
 *
 * <p>本类会被服务端加载（数据层混入要用 idOf/holderOf），绝不能引用 {@code I18n}
 * 这类客户端类。翻译在客户端界面里做。
 */
public final class MeleeBuffUtil {

    private MeleeBuffUtil() {
    }

    /** 原版数值 id（含点燃哨兵值 666）→ 注册名。旧存档迁移用。 */
    public static String nameOfNumeric(int id) {
        if (id == 666) return MeleeBuff.FIRE;
        if (id == 0) return null;
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.byId(id);
        if (effect == null) return null;
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return key == null ? null : key.toString();
    }

    /** 注册名 → Holder。查不到（对应 mod 被移除）或点燃哨兵值返回 null。 */
    public static Holder<MobEffect> holderOf(String name) {
        if (name == null || name.isEmpty() || MeleeBuff.FIRE.equals(name)) return null;
        ResourceLocation rl = ResourceLocation.tryParse(name);
        if (rl == null) return null;
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.getHolder(rl);
        return holder.isPresent() ? holder.get() : null;
    }

    /** 效果的翻译键。点燃没有注册表项，用原版火焰的翻译键。 */
    public static String descriptionIdOf(String name) {
        if (MeleeBuff.FIRE.equals(name)) return "block.minecraft.fire";
        Holder<MobEffect> holder = holderOf(name);
        if (holder == null) return name;
        return holder.value().getDescriptionId();
    }

    /** 全部可选效果的注册名，含点燃哨兵值，用于界面左侧「可用」列表。 */
    public static List<String> allEffectIds() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation rl : BuiltInRegistries.MOB_EFFECT.keySet()) {
            ids.add(rl.toString());
        }
        // 点燃不是药水效果，但原版界面把它作为最后一项提供，这里保持一致。
        ids.add(MeleeBuff.FIRE);
        return ids;
    }
}
