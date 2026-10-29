package top.cnpcplus.drop.fx.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * 粒子→图标纹理映射（233d，纯客户端）。
 *
 * <p><b>映射规则（2026-10-17 用户反馈"几乎全 ? "后全量重建）</b>：
 * 数据源=1.20.1 client-extra.jar 内
 * {@code assets/minecraft/particles/*.json}（vanilla 88 个粒子定义，
 * 每个 JSON 的 textures 数组第一项=该粒子首帧 sprite 名）+
 * {@code assets/minecraft/textures/particle/*.png} 文件清单+PNG 头尺寸。
 * 一张不猜、全部实证——旧映射 flame_0/glow_0/crit_0/happy_villager_0 等
 * 文件名对不上的一律删除（路径不存在=品红 missing texture，比 '?' 更糟）。
 *
 * <p><b>共用 sprite 说明</b>：大量粒子共用 generic_7（dust/smoke/poof/
 * cloud/snowflake/spit/sneeze 等）或 glint（happy_villager/egg_crack/
 * composter）或 drip_hang/drip_fall/drip_land（各类 dripping/landing）——
 * 图标相同但粒子行为不同，符合 vanilla 视觉（这些粒子本来就长得像）。
 *
 * <p><b>多帧条带</b>：vanilla 把动画粒子拆成独立文件（sweep_0..7 各自
 * 8×8/32×32 单帧），唯 vibration 是 18×126 单文件 7 帧条带——用
 * srcW/srcH=18×18 裁首帧、texW/texH=18×126 声明整图。
 *
 * <p><b>拿不到映射的粒子画黄色问号</b>（用户拍板：不是不显示）。
 * mod 粒子几乎全走问号（各 mod 纹理路径不可枚举）。
 */
public final class ParticleIconLookup {

    /**
     * 图标描述。
     *
     * @param texture 纹理路径
     * @param srcW    blit 源区宽（多帧条带=单帧宽；单帧图=texW）
     * @param srcH    blit 源区高（多帧条带=单帧高；单帧图=texH）
     * @param texW    纹理整图宽（blit 必须声明真实宽，否则采样错位）
     * @param texH    纹理整图高
     */
    public record Icon(ResourceLocation texture, int srcW, int srcH, int texW, int texH) {
        public static final int DRAW = 8;
    }

    private static final Map<String, Icon> ICONS = new HashMap<>();

    static {
        // —— 独立文件粒子（每文件单帧）——
        icon("minecraft:flame", "flame", 8, 8);
        icon("minecraft:small_flame", "flame", 8, 8);
        icon("minecraft:crit", "critical_hit", 8, 8);
        icon("minecraft:sweep_attack", "sweep_0", 32, 32);
        icon("minecraft:heart", "heart", 8, 8);
        icon("minecraft:angry_villager", "angry", 8, 8);
        icon("minecraft:note", "note", 8, 8);
        icon("minecraft:lava", "lava", 8, 8);
        icon("minecraft:glow", "glow", 8, 8);
        icon("minecraft:electric_spark", "glow", 8, 8);
        icon("minecraft:scrape", "glow", 8, 8);
        icon("minecraft:wax_on", "glow", 8, 8);
        icon("minecraft:wax_off", "glow", 8, 8);
        icon("minecraft:sonic_boom", "sonic_boom_0", 32, 32);
        icon("minecraft:bubble", "bubble", 8, 8);
        icon("minecraft:bubble_column_up", "bubble", 8, 8);
        icon("minecraft:current_down", "bubble", 8, 8);
        icon("minecraft:splash", "splash_0", 8, 8);
        icon("minecraft:fishing", "splash_0", 8, 8);
        icon("minecraft:rain", "splash_0", 8, 8);
        icon("minecraft:enchanted_hit", "enchanted_hit", 8, 8);
        icon("minecraft:soul_fire_flame", "soul_fire_flame", 8, 8);
        icon("minecraft:nautilus", "nautilus", 8, 8);
        icon("minecraft:damage_indicator", "damage", 8, 8);
        icon("minecraft:bubble_pop", "bubble_pop_0", 16, 16);
        icon("minecraft:cherry_leaves", "cherry_0", 3, 3);
        icon("minecraft:sculk_charge", "sculk_charge_0", 16, 16);
        icon("minecraft:sculk_charge_pop", "sculk_charge_pop_0", 16, 16);
        icon("minecraft:sculk_soul", "sculk_soul_0", 16, 16);
        icon("minecraft:soul", "soul_0", 16, 16);
        icon("minecraft:campfire_cosy_smoke", "big_smoke_0", 16, 16);
        icon("minecraft:campfire_signal_smoke", "big_smoke_0", 16, 16);
        icon("minecraft:explosion", "explosion_0", 32, 32);
        icon("minecraft:flash", "flash", 32, 32);
        icon("minecraft:shriek", "shriek", 13, 13);
        icon("minecraft:enchant", "sga_a", 8, 8);
        icon("minecraft:totem_of_undying", "glitter_7", 8, 8);
        icon("minecraft:end_rod", "glitter_7", 8, 8);

        // —— drip 系列：hang(悬挂)/fall(下落)/land(落地) 三形态 ——
        icon("minecraft:dripping_water", "drip_hang", 8, 8);
        icon("minecraft:dripping_lava", "drip_hang", 8, 8);
        icon("minecraft:dripping_honey", "drip_hang", 8, 8);
        icon("minecraft:dripping_dripstone_water", "drip_hang", 8, 8);
        icon("minecraft:dripping_dripstone_lava", "drip_hang", 8, 8);
        icon("minecraft:dripping_obsidian_tear", "drip_hang", 8, 8);
        icon("minecraft:falling_water", "drip_fall", 8, 8);
        icon("minecraft:falling_lava", "drip_fall", 8, 8);
        icon("minecraft:falling_honey", "drip_fall", 8, 8);
        icon("minecraft:falling_nectar", "drip_fall", 8, 8);
        icon("minecraft:falling_dripstone_water", "drip_fall", 8, 8);
        icon("minecraft:falling_dripstone_lava", "drip_fall", 8, 8);
        icon("minecraft:falling_obsidian_tear", "drip_fall", 8, 8);
        icon("minecraft:spore_blossom_air", "drip_fall", 8, 8);
        icon("minecraft:falling_spore_blossom", "drip_fall", 8, 8);
        icon("minecraft:landing_lava", "drip_land", 8, 8);
        icon("minecraft:landing_honey", "drip_land", 8, 8);
        icon("minecraft:landing_obsidian_tear", "drip_land", 8, 8);

        // —— glint（happy_villager 等的贴图在 1.20.1 是 glint）——
        icon("minecraft:happy_villager", "glint", 8, 8);
        icon("minecraft:egg_crack", "glint", 8, 8);
        icon("minecraft:composter", "glint", 8, 8);

        // —— generic 系列（8 帧各是独立文件；取末帧最亮的 generic_7 或首帧 generic_0）——
        icon("minecraft:dust", "generic_7", 8, 8);
        icon("minecraft:dust_color_transition", "generic_7", 8, 8);
        icon("minecraft:smoke", "generic_7", 8, 8);
        icon("minecraft:large_smoke", "generic_7", 8, 8);
        icon("minecraft:poof", "generic_7", 8, 8);
        icon("minecraft:cloud", "generic_7", 8, 8);
        icon("minecraft:snowflake", "generic_7", 8, 8);
        icon("minecraft:spit", "generic_7", 8, 8);
        icon("minecraft:sneeze", "generic_7", 8, 8);
        icon("minecraft:squid_ink", "generic_7", 8, 8);
        icon("minecraft:glow_squid_ink", "generic_7", 8, 8);
        icon("minecraft:falling_dust", "generic_7", 8, 8);
        icon("minecraft:ash", "generic_0", 8, 8);
        icon("minecraft:white_ash", "generic_0", 8, 8);
        icon("minecraft:warped_spore", "generic_0", 8, 8);
        icon("minecraft:crimson_spore", "generic_0", 8, 8);
        icon("minecraft:mycelium", "generic_0", 8, 8);
        icon("minecraft:underwater", "generic_0", 8, 8);
        icon("minecraft:dolphin", "generic_0", 8, 8);
        icon("minecraft:portal", "generic_0", 8, 8);
        icon("minecraft:reverse_portal", "generic_0", 8, 8);
        icon("minecraft:dragon_breath", "generic_5", 8, 8);

        // —— effect/spell 系列 ——
        icon("minecraft:effect", "effect_7", 8, 8);
        icon("minecraft:entity_effect", "effect_7", 8, 8);
        icon("minecraft:ambient_entity_effect", "effect_7", 8, 8);
        icon("minecraft:witch", "spell_7", 8, 8);
        icon("minecraft:instant_effect", "spell_7", 8, 8);

        // —— firework 用 spark 系列 ——
        icon("minecraft:firework", "spark_7", 8, 8);

        // —— vibration：唯一单文件多帧条带 18×126（7 帧 18×18），裁首帧 ——
        ICONS.put("minecraft:vibration", new Icon(
                new ResourceLocation("minecraft", "textures/particle/vibration.png"),
                18, 18, 18, 126));

        // —— vanilla 88 粒子全部覆盖；mod 粒子走 '?' ——
    }

    private static void icon(String id, String tex, int w, int h) {
        ICONS.put(id, new Icon(
                new ResourceLocation("minecraft", "textures/particle/" + tex + ".png"),
                w, h, w, h));
    }

    private ParticleIconLookup() {}

    /** 返回图标；无映射返回 null（调用方画黄 '?'）。 */
    public static Icon of(String particleId) {
        return ICONS.get(particleId);
    }
}
