package bin.cnpcplus.transform;

/**
 * 记住玩家在「夜间设置」界面最近点的是「加载白天设置」还是「加载夜间设置」。
 *
 * <p>存在的理由：{@code DataTransform} 没有「当前编辑的是日还是夜」这个字段，
 * 只有 {@code editingModus}（是否处于编辑模式）与 {@code isActive}（是否处于变形态）。
 * 原版按钮 11/12（{@code GuiNPCNightSetup:67-68}）发的 {@code SPacketNpcTransform}
 * 是一次性动作包（参数 false=日、true=夜），服务端处理完不回传「当前在编辑哪一套」。
 *
 * <p>为什么不用 {@code isActive}：它表示 NPC 当前是否处于变形后的形态，是运行期状态，
 * 与「我正在编辑哪一套数据」是两件事。拿它当判据会在 NPC 恰好处于变形态时显示错误标题。
 * 这与哈基彬的表述一致：「现在我配置的是夜间模式」指的就是刚点过「加载夜间设置」。
 *
 * <p>纯客户端的单个静态字段：只是给编辑者看的界面提示，不参与数据判定、不落盘、不同步；
 * 同一时刻只可能有一个玩家在一个界面里编辑，不需要按 NPC 分表。
 * 默认 false（日）—— 原版在编辑模式开启时并不预先加载任何一套，玩家必须先点一次按钮才有意义。
 *
 * <p>刻意放在 mixin 包之外，避免 IllegalClassLoadError。
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
