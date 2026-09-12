package bin.cnpcplus.melee;

/**
 * 一条近战附加 BUFF。
 *
 * <p>与 1.12.2 的 {@code MeleeBuffStore} 语义一致：<b>主键是注册名字符串</b>，
 * 不是数值 id。原因是注册表数值 id 由运行期注册顺序决定，增删 mod 或改加载顺序
 * 都会让同一个 id 指向不同效果，存档就会串味；注册名是稳定标识，
 * 也是原版药水存档自己用的方式。
 *
 * @param effectId 注册名，例如 {@code "minecraft:speed"}；点燃用 {@code "cnpcplus:fire"}
 * @param amp      放大等级（0 即 I 级），与原版 {@code MobEffectInstance} 一致
 * @param seconds  持续时间，单位秒（与原版界面同单位，施加时 ×20 转 tick）
 */
public final class MeleeBuff {

    /** 点燃的伪注册名。原版用数值 666 当哨兵值，这里换成稳定的字符串。 */
    public static final String FIRE = "cnpcplus:fire";

    /** 单项上限，防止误操作把整表两百多个效果全加上导致每次命中刷屏。 */
    public static final int MAX_ENTRIES = 32;

    public static final int MAX_AMP = 255;
    public static final int MAX_SECONDS = 99999;

    public final String effectId;
    public final int amp;
    public final int seconds;

    public MeleeBuff(String effectId, int amp, int seconds) {
        this.effectId = effectId;
        this.amp = clampAmp(amp);
        this.seconds = clampSeconds(seconds);
    }

    public static int clampAmp(int value) {
        if (value < 0) return 0;
        return Math.min(value, MAX_AMP);
    }

    public static int clampSeconds(int value) {
        if (value < 1) return 1;
        return Math.min(value, MAX_SECONDS);
    }
}
