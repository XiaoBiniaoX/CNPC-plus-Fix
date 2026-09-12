package top.cnpcplus.transform;

/**
 * 记住玩家在「夜晚设置」界面最近点的是「载入白天」还是「载入夜晚」。
 *
 * <p>存在的理由：{@code DataTransform} 没有这个字段（只有 {@code editingModus}
 * 与 {@code isActive}），而原版按钮 11/12 发的 {@code SPacketNpcTransform} 是
 * 一次性动作包，服务端不回传「当前在编辑哪一套」。详见
 * {@code MixinGuiNPCNightSetupTitle} 的类注释。
 *
 * <h3>为什么是纯客户端的单个静态字段</h3>
 * 这只是给编辑者看的界面提示，不参与任何数据判定，也不需要落盘或同步。
 * 同一时刻只可能有一个玩家在一个界面里编辑，所以不需要按 NPC 分表。
 * 刻意不放在 mixin 包内（mixin 包内的类不能被外部直接引用）。
 *
 * <h3>默认值</h3>
 * 默认 false（日）。原版界面在编辑模式开启时并不预先加载任何一套，
 * 玩家必须先点一次按钮才有意义；在他点之前显示「日间模式」是保守选择。
 */
public final class TransformEditModeStore {

    private static boolean night = false;

    private TransformEditModeStore() {
    }

    public static boolean isNight() {
        return night;
    }

    public static void setNight(boolean value) {
        night = value;
    }
}
