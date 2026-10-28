package bin.cnpcplus.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * 投掷物跟随的常用图案预设。
 *
 * <p>预设只是"一组 FxShape"，写进当前粒子的配置里，运行时不特殊对待；
 * 这样服务端只认 FxConfig 本身，跨端一致，也不影响已有的形状编辑。</p>
 */
public final class FxPresets {
    public static final String[] KEYS = {"ring", "helix", "vertical", "burst"};

    private FxPresets() { }

    public static String langKey(int index) { return "cnpcplus.fx.preset." + KEYS[Math.max(0, Math.min(KEYS.length - 1, index))]; }

    public static List<FxShape> build(int index) {
        List<FxShape> shapes = new ArrayList<>();
        switch (Math.floorMod(index, KEYS.length)) {
            case 0: // 环绕光环：水平圆环，绕自身转
                shapes.add(shape(ShapeType.CIRCLE, .8, 20, 1, .25));
                break;
            case 1: // 螺旋尾迹：双螺旋 + 绕 Z 自转，看起来像绕着投掷物转圈前进
                FxShape helix = shape(ShapeType.DOUBLE_SPIRAL, .45, 30, 1.2, .3);
                helix.set(5, .2);
                shapes.add(helix);
                break;
            case 2: // 垂直光环：把圆环竖起来，正对飞行方向
                FxShape vertical = shape(ShapeType.CIRCLE, .7, 20, 1, .2);
                vertical.set(4, 90);
                shapes.add(vertical);
                break;
            default: // 爆散火花：随机点 + 小环，短促
                shapes.add(shape(ShapeType.RANDOM, .9, 10, 1.4, 0));
                shapes.add(shape(ShapeType.RING_DOTS, .35, 10, .5, .4));
                break;
        }
        return shapes;
    }

    private static FxShape shape(ShapeType type, double radius, double duration, double density, double spin) {
        FxShape shape = new FxShape();
        shape.type = type;
        shape.set(0, radius);
        shape.set(8, duration);
        shape.set(9, density);
        shape.set(1, spin);
        return shape;
    }
}
