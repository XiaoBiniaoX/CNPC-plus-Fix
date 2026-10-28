package bin.cnpcplus.fx;

import java.util.Random;

/** 1.20.1 粒子框架的 25 种 XZ 平面形状，纯数学，可供运行时和画廊共用。 */
public enum ShapeType {
    CIRCLE, SQUARE, TRIANGLE, PENTAGON, HEXAGON, PENTA_STAR, HEX_STAR,
    DIAMOND, RECT_FRAME, ELLIPSE, HEART, SPIRAL, DOUBLE_SPIRAL, LINE,
    CROSS, X_SHAPE, T_SHAPE, L_SHAPE, ARROW, WAVE, GRID, DISC,
    SQUARE_FILL, RANDOM, RING_DOTS, DIAMOND_STAR;

    public double[] sample(int count, long seed) {
        count = Math.max(1, Math.min(256, count));
        double[] points = new double[count * 2];
        Random random = new Random(seed);
        for (int i = 0; i < count; i++) {
            double t = (double) i / count, f = (double) i / Math.max(1, count - 1);
            double a = t * Math.PI * 2, x = Math.cos(a), z = Math.sin(a);
            double[] p = null;
            switch (this) {
                case SQUARE: p = polygon(t, 4, Math.PI / 4); break;
                case TRIANGLE: p = polygon(t, 3, -Math.PI / 2); break;
                case PENTAGON: p = polygon(t, 5, -Math.PI / 2); break;
                case HEXAGON: p = polygon(t, 6, -Math.PI / 2); break;
                case PENTA_STAR: p = star(t, 5, .45); break;
                case HEX_STAR: p = star(t, 6, .55); break;
                case DIAMOND_STAR: p = star(t, 4, .4); break;
                case DIAMOND: double d = Math.abs(x) + Math.abs(z); x /= d; z /= d; break;
                case RECT_FRAME: p = polygon(t, 4, Math.PI / 4); p[0] *= Math.sqrt(2); p[1] *= Math.sqrt(2) * .6; break;
                case ELLIPSE: z *= .6; break;
                case HEART:
                    x = 16 * Math.pow(Math.sin(a), 3) / 17;
                    z = -(13 * Math.cos(a) - 5 * Math.cos(2 * a) - 2 * Math.cos(3 * a) - Math.cos(4 * a)) / 17;
                    break;
                case SPIRAL: a = 3 * Math.PI * f; x = Math.cos(a) * f; z = Math.sin(a) * f; break;
                case DOUBLE_SPIRAL:
                    f = (double) (i / 2) / Math.max(1, (count - 1) / 2);
                    a = 3 * Math.PI * f + (i % 2) * Math.PI;
                    x = Math.cos(a) * f; z = Math.sin(a) * f; break;
                case LINE: x = -1 + 2 * f; z = 0; break;
                case CROSS:
                case X_SHAPE:
                case T_SHAPE:
                case L_SHAPE:
                    f = (double) (i / 2) / Math.max(1, (count - 1) / 2);
                    double v = -1 + 2 * f;
                    if (this == CROSS) { x = i % 2 == 0 ? v : 0; z = i % 2 == 0 ? 0 : v; }
                    if (this == X_SHAPE) { x = v; z = i % 2 == 0 ? v : -v; }
                    if (this == T_SHAPE) { x = i % 2 == 0 ? v : 0; z = i % 2 == 0 ? -.5 : -.5 + f; }
                    if (this == L_SHAPE) { x = i % 2 == 0 ? -.6 : -.6 + f; z = i % 2 == 0 ? -.5 + f : .5; }
                    break;
                case ARROW:
                    if (t < .5) { x = -1 + t * 4; z = 0; }
                    else { double h = ((t - .5) * 4) % 1; x = 1 - .6 * h; z = (t < .75 ? -.6 : .6) * h; }
                    break;
                case WAVE: x = -1 + 2 * f; z = Math.sin(f * Math.PI * 3) * .5; break;
                case GRID: x = -1 + random.nextInt(5) * .5; z = -1 + random.nextInt(5) * .5; break;
                case DISC:
                case RANDOM: a = random.nextDouble() * Math.PI * 2; double r = Math.sqrt(random.nextDouble()); x = Math.cos(a) * r; z = Math.sin(a) * r; break;
                case SQUARE_FILL: x = random.nextDouble() * 2 - 1; z = random.nextDouble() * 2 - 1; break;
                case RING_DOTS: a = Math.PI * 2 * (i % 16) / Math.min(16, count); x = Math.cos(a); z = Math.sin(a); break;
                default: break;
            }
            points[i * 2] = p == null ? x : p[0];
            points[i * 2 + 1] = p == null ? z : p[1];
        }
        return points;
    }

    private static double[] polygon(double t, int sides, double phase) {
        double scaled = t * sides;
        int edge = (int) scaled;
        double f = scaled - edge;
        double a = phase + Math.PI * 2 * edge / sides;
        double b = phase + Math.PI * 2 * (edge + 1) / sides;
        return new double[]{Math.cos(a) + (Math.cos(b) - Math.cos(a)) * f,
                Math.sin(a) + (Math.sin(b) - Math.sin(a)) * f};
    }

    private static double[] star(double t, int tips, double inner) {
        double scaled = t * tips * 2;
        int edge = (int) scaled;
        double f = scaled - edge, r0 = edge % 2 == 0 ? 1 : inner, r1 = edge % 2 == 0 ? inner : 1;
        double a = -Math.PI / 2 + Math.PI * edge / tips, b = a + Math.PI / tips;
        return new double[]{Math.cos(a) * r0 + (Math.cos(b) * r1 - Math.cos(a) * r0) * f,
                Math.sin(a) * r0 + (Math.sin(b) * r1 - Math.sin(a) * r0) * f};
    }
}
