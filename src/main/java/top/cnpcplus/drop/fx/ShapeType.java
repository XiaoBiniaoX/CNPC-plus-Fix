package top.cnpcplus.drop.fx;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 形状预设类型（233d，25 种）：每种是一个「单位形状采样器」——
 * 在 XZ 平面单位圆 [-1,1] 内输出 count 个采样点，y 恒 0。
 * 半径/内陷外扩/旋转/偏移由 {@link DropFxSampler} 统一后处理，枚举只管形状本身。
 *
 * <p><b>可复用说明</b>：新增形状=加枚举常量实现 {@link #sample}，其余零改动。
 * 采样是纯数学（RandomSource 可注入固定种子→确定性预览），无任何世界依赖，可后台线程跑。
 */
public enum ShapeType {

    /** 圆（边界均匀角度）。 */
    CIRCLE {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * i / count;
                out.add(new double[]{Math.cos(a), Math.sin(a)});
            }
        }
    },
    /** 正方形（边界按边均匀）。 */
    SQUARE {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            perimeterOf(count, 4, a -> squarePoint(a), out);
        }
    },
    /** 三角形（边界）。 */
    TRIANGLE {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            perimeterOf(count, 3, a -> regularPolygonPoint(a, 3), out);
        }
    },
    /** 五边形。 */
    PENTAGON {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            perimeterOf(count, 5, a -> regularPolygonPoint(a, 5), out);
        }
    },
    /** 六边形。 */
    HEXAGON {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            perimeterOf(count, 6, a -> regularPolygonPoint(a, 6), out);
        }
    },
    /** 五角星（5 顶点向内凹，星形边界）。 */
    PENTA_STAR {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            starOf(count, 5, 0.45, out);
        }
    },
    /** 六芒星（两正三角叠加的 12 顶点星）。 */
    HEX_STAR {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            starOf(count, 6, 0.55, out);
        }
    },
    /** 菱形。 */
    DIAMOND {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            // 菱形=旋转45°正方形的 L1 球边界
            for (int i = 0; i < count; i++) {
                double t = Math.PI * 2 * i / count;
                double c = Math.abs(Math.cos(t)), s = Math.abs(Math.sin(t));
                double d = c + s;
                out.add(new double[]{Math.cos(t) / d, Math.sin(t) / d});
            }
        }
    },
    /** 矩形框（宽 2 高 1.2）。 */
    RECT_FRAME {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            double hw = 1.0, hh = 0.6;
            double per = 4 * (hw + hh);
            for (int i = 0; i < count; i++) {
                double d = per * i / count;
                double x, z;
                if (d < 2 * hw) { x = -hw + d; z = -hh; }
                else if (d < 2 * hw + hh) { x = hw; z = -hh + (d - 2 * hw); }
                else if (d < 4 * hw + hh) { x = hw - (d - 2 * hw - hh); z = hh; }
                else { x = -hw; z = hh - (d - 4 * hw - hh); }
                out.add(new double[]{x, z});
            }
        }
    },
    /** 椭圆（x 半轴 1、z 半轴 0.6）。 */
    ELLIPSE {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * i / count;
                out.add(new double[]{Math.cos(a), Math.sin(a) * 0.6});
            }
        }
    },
    /** 心形（经典参数方程，翻转朝 +Z）。 */
    HEART {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            for (int i = 0; i < count; i++) {
                double t = Math.PI * 2 * i / count;
                double x = 16 * Math.pow(Math.sin(t), 3);
                double z = -(13 * Math.cos(t) - 5 * Math.cos(2 * t)
                        - 2 * Math.cos(3 * t) - Math.cos(4 * t));
                out.add(new double[]{x / 17.0, z / 17.0});
            }
        }
    },
    /** 阿基米德螺旋（1.5 圈）。 */
    SPIRAL {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            double turns = 1.5;
            for (int i = 0; i < count; i++) {
                double f = (double) i / Math.max(1, count - 1);
                double a = Math.PI * 2 * turns * f;
                double r = f; // 0→1
                out.add(new double[]{Math.cos(a) * r, Math.sin(a) * r});
            }
        }
    },
    /** 双螺旋（相位差 π）。 */
    DOUBLE_SPIRAL {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            double turns = 1.5;
            for (int i = 0; i < count; i++) {
                double f = (double) i / Math.max(1, count - 1);
                double a = Math.PI * 2 * turns * f;
                double r = f;
                out.add(new double[]{Math.cos(a) * r, Math.sin(a) * r});
                out.add(new double[]{Math.cos(a + Math.PI) * r, Math.sin(a + Math.PI) * r});
            }
        }
    },
    /** 直线（沿 X 轴 [-1,1]）。 */
    LINE {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            for (int i = 0; i < count; i++) {
                double f = count == 1 ? 0 : (double) i / (count - 1);
                out.add(new double[]{-1 + 2 * f, 0});
            }
        }
    },
    /** 十字（两线段）。 */
    CROSS {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            int half = count / 2;
            for (int i = 0; i < half; i++) {
                double f = half == 1 ? 0 : (double) i / (half - 1);
                out.add(new double[]{-1 + 2 * f, 0});
                out.add(new double[]{0, -1 + 2 * f});
            }
        }
    },
    /** X 形（两斜线）。 */
    X_SHAPE {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            int half = count / 2;
            for (int i = 0; i < half; i++) {
                double f = half == 1 ? 0 : (double) i / (half - 1);
                double v = -1 + 2 * f;
                out.add(new double[]{v, v});
                out.add(new double[]{v, -v});
            }
        }
    },
    /** T 形（横线+中垂线）。 */
    T_SHAPE {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            int half = count / 2;
            for (int i = 0; i < half; i++) {
                double f = half == 1 ? 0 : (double) i / (half - 1);
                out.add(new double[]{-1 + 2 * f, -0.5});
                if (i > 0) out.add(new double[]{0, -0.5 + f}); // 去重中心
            }
        }
    },
    /** L 形（竖线+底横线）。 */
    L_SHAPE {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            int half = count / 2;
            for (int i = 0; i < half; i++) {
                double f = half == 1 ? 0 : (double) i / (half - 1);
                out.add(new double[]{-0.6, -0.5 + f});
                out.add(new double[]{-0.6 + f, 0.5});
            }
        }
    },
    /** 箭头（指向 +X：主线+两尾羽）。 */
    ARROW {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            int shaft = count / 2, head = count - shaft;
            for (int i = 0; i < shaft; i++) {
                double f = shaft == 1 ? 0 : (double) i / (shaft - 1);
                out.add(new double[]{-1 + 0.7 * f, 0});
            }
            for (int i = 0; i < head / 2; i++) {
                double f = head < 2 ? 0 : (double) i / Math.max(1, head / 2 - 1);
                out.add(new double[]{0.4 + 0.6 * f, -0.6 * f});
                out.add(new double[]{0.4 + 0.6 * f, 0.6 * f});
            }
        }
    },
    /** 波浪线（sin，沿 X）。 */
    WAVE {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            for (int i = 0; i < count; i++) {
                double f = (double) i / Math.max(1, count - 1);
                out.add(new double[]{-1 + 2 * f, Math.sin(f * Math.PI * 3) * 0.5});
            }
        }
    },
    /** 网格方阵（5×5 格点，随机落点凑数）。 */
    GRID {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            for (int i = 0; i < count; i++) {
                int gx = rnd.nextInt(5), gz = rnd.nextInt(5);
                out.add(new double[]{-1 + gx * 0.5, -1 + gz * 0.5});
            }
        }
    },
    /** 实心圆盘（径向随机）。 */
    DISC {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * rnd.nextDouble();
                double r = Math.sqrt(rnd.nextDouble());
                out.add(new double[]{Math.cos(a) * r, Math.sin(a) * r});
            }
        }
    },
    /** 实心正方形。 */
    SQUARE_FILL {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            for (int i = 0; i < count; i++) {
                out.add(new double[]{rnd.nextDouble() * 2 - 1, rnd.nextDouble() * 2 - 1});
            }
        }
    },
    /** 随机散布（单位圆盘内）。 */
    RANDOM {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            DISC.sample(count, rnd, out);
        }
    },
    /** 圆环点阵（16 等分固定点，适合"宝石阵"）。 */
    RING_DOTS {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            int n = Math.min(count, 16);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                out.add(new double[]{Math.cos(a), Math.sin(a)});
            }
            for (int i = n; i < count; i++) { // 超出部分随机补
                double a = Math.PI * 2 * rnd.nextDouble();
                out.add(new double[]{Math.cos(a), Math.sin(a)});
            }
        }
    },
    /** 菱形星（4 角星，十字装饰）。 */
    DIAMOND_STAR {
        @Override public void sample(int count, RandomSource rnd, List<double[]> out) {
            starOf(count, 4, 0.4, out);
        }
    };

    /** 输出 count 个单位点 [x,z] 到 out（调用方负责清空；本方法不保证 out 尺寸上限）。 */
    public abstract void sample(int count, RandomSource rnd, List<double[]> out);

    // ===== 共享几何工具 =====

    /** 正 n 边形边界参数化：a∈[0,1) 归一化周长位置→[x,z]。fn 接收归一化位置。 */
    private interface UnitPointFn { double[] at(double a); }

    private static void perimeterOf(int count, int sides, UnitPointFn fn, List<double[]> out) {
        for (int i = 0; i < count; i++) {
            out.add(fn.at((double) i / count));
        }
    }

    /** 正 n 边形边界（顶点在角度 -π/2 起），a=归一化周长。 */
    private static double[] regularPolygonPoint(double a, int n) {
        double seg = 1.0 / n;
        int side = Math.min(n - 1, (int) (a / seg));
        double local = a - side * seg; // 0..1 本边内
        double a0 = -Math.PI / 2 + Math.PI * 2 * side / n;
        double a1 = -Math.PI / 2 + Math.PI * 2 * (side + 1) / n;
        double x0 = Math.cos(a0), z0 = Math.sin(a0);
        double x1 = Math.cos(a1), z1 = Math.sin(a1);
        return new double[]{x0 + (x1 - x0) * local, z0 + (z1 - z0) * local};
    }

    /** 旋转正方形边界（45°），a=归一化周长。 */
    private static double[] squarePoint(double a) {
        return regularPolygonPoint(a + 0.125, 4); // 相位 45° 成菱→方形观感由顶点角度决定
    }

    /** n 角星：外顶点单位圆、内顶点缩至 inner，星形边界折线。 */
    private static void starOf(int count, int n, double inner, List<double[]> out) {
        int tips = n * 2;
        for (int i = 0; i < count; i++) {
            double t = (double) i / count * tips;
            int seg = (int) t;
            double f = t - seg;
            double r0 = (seg % 2 == 0) ? 1.0 : inner;
            double r1 = ((seg + 1) % 2 == 0) ? 1.0 : inner;
            double a0 = -Math.PI / 2 + Math.PI * 2 * seg / tips;
            double a1 = -Math.PI / 2 + Math.PI * 2 * (seg + 1) / tips;
            double x = lerp(Math.cos(a0) * r0, Math.cos(a1) * r1, f);
            double z = lerp(Math.sin(a0) * r0, Math.sin(a1) * r1, f);
            out.add(new double[]{x, z});
        }
    }

    private static double lerp(double a, double b, double f) { return a + (b - a) * f; }
}
