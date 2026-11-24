package bin.cnpcplus.script.tools;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ScriptUI {
    public static final class Size2 {
        private final double width, height;
        public Size2(double width, double height) { this.width = width; this.height = height; }
        public double width() { return width; } public double height() { return height; }
    }
    public static final class Point2 {
        private final double x, y;
        public Point2(double x, double y) { this.x = x; this.y = y; }
        public double x() { return x; } public double y() { return y; }
    }
    public static final class Rect {
        private final double x, y, width, height;
        public Rect(double x, double y, double width, double height) { this.x = x; this.y = y; this.width = width; this.height = height; }
        public double x() { return x; } public double y() { return y; }
        public double width() { return width; } public double height() { return height; }
    }
    public static final class Insets {
        private final double top, right, bottom, left;
        public Insets(double top, double right, double bottom, double left) { this.top = top; this.right = right; this.bottom = bottom; this.left = left; }
        public double top() { return top; } public double right() { return right; } public double bottom() { return bottom; } public double left() { return left; }
    }
    public static final class Constraints {
        private final double minWidth, maxWidth, minHeight, maxHeight;
        public Constraints(double minWidth, double maxWidth, double minHeight, double maxHeight) {
            this.minWidth = minWidth; this.maxWidth = maxWidth; this.minHeight = minHeight; this.maxHeight = maxHeight;
        }
        public double minWidth() { return minWidth; } public double maxWidth() { return maxWidth; }
        public double minHeight() { return minHeight; } public double maxHeight() { return maxHeight; }
    }
    public final Scale Scale = new Scale();
    public final ScriptMeasure Measure = new ScriptMeasure();
    public final Layout Layout = new Layout();
    public Size2 size(double width, double height) { return new Size2(width, height); }
    public Point2 point(double x, double y) { return new Point2(x, y); }
    public Rect rect(double x, double y, double width, double height) { return new Rect(x, y, width, height); }
    public Insets insets(double top, double right, double bottom, double left) { return new Insets(top, right, bottom, left); }
    public Constraints constraints(double minWidth, double maxWidth, double minHeight, double maxHeight) {
        if (!(minWidth >= 0 && minWidth <= maxWidth && minHeight >= 0 && minHeight <= maxHeight)) throw new IllegalArgumentException("Invalid constraints");
        return new Constraints(minWidth, maxWidth, minHeight, maxHeight);
    }
    public static final class Scale {
        public double toGui(double value, double scale) { positiveScale(scale); return value / scale; }
        public double toScreen(double value, double scale) { positiveScale(scale); return value * scale; }
        private void positiveScale(double scale) { if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Scale must be finite and positive"); }
    }
    public static final class Layout {
        public Rect center(Rect parent, Size2 size) { return align(parent, size, "CENTER"); }
        public Rect align(Rect parent, Size2 size, String alignment) {
            double x = parent.width - size.width, y = parent.height - size.height;
            switch (alignment) {
                case "TOP_LEFT": return new Rect(parent.x, parent.y, size.width, size.height);
                case "TOP": return new Rect(parent.x + x / 2, parent.y, size.width, size.height);
                case "TOP_RIGHT": return new Rect(parent.x + x, parent.y, size.width, size.height);
                case "LEFT": return new Rect(parent.x, parent.y + y / 2, size.width, size.height);
                case "CENTER": return new Rect(parent.x + x / 2, parent.y + y / 2, size.width, size.height);
                case "RIGHT": return new Rect(parent.x + x, parent.y + y / 2, size.width, size.height);
                case "BOTTOM_LEFT": return new Rect(parent.x, parent.y + y, size.width, size.height);
                case "BOTTOM": return new Rect(parent.x + x / 2, parent.y + y, size.width, size.height);
                case "BOTTOM_RIGHT": return new Rect(parent.x + x, parent.y + y, size.width, size.height);
                default: throw new IllegalArgumentException("Unknown alignment");
            }
        }
        public Rect anchor(Rect parent, Size2 size, String anchor, Point2 offset) { Rect r = align(parent, size, anchor); return new Rect(r.x + offset.x, r.y + offset.y, r.width, r.height); }
        public Rect inset(Rect r, Insets in) { return new Rect(r.x + in.left, r.y + in.top, Math.max(0, r.width - in.left - in.right), Math.max(0, r.height - in.top - in.bottom)); }
        public Rect expand(Rect r, Insets in) { return inset(r, new Insets(-in.top, -in.right, -in.bottom, -in.left)); }
        public Rect clamp(Rect r, Rect bounds) {
            double w = Math.min(r.width, bounds.width), h = Math.min(r.height, bounds.height);
            return new Rect(Math.max(bounds.x, Math.min(r.x, bounds.x + bounds.width - w)), Math.max(bounds.y, Math.min(r.y, bounds.y + bounds.height - h)), w, h);
        }
        public Size2 fit(Size2 size, Constraints limits) {
            return new Size2(Math.max(limits.minWidth, Math.min(size.width, limits.maxWidth)), Math.max(limits.minHeight, Math.min(size.height, limits.maxHeight)));
        }
        public List<Rect> split(Rect r, String direction, double ratio, double gap) {
            if (!(ratio >= 0 && ratio <= 1 && gap >= 0) || !(direction.equals("HORIZONTAL") || direction.equals("VERTICAL"))) throw new IllegalArgumentException("Invalid split");
            boolean horizontal = direction.equals("HORIZONTAL"); double space = horizontal ? r.width : r.height;
            gap = Math.min(gap, space); double first = (space - gap) * ratio, second = space - gap - first;
            return Collections.unmodifiableList(horizontal ? Arrays.asList(new Rect(r.x, r.y, first, r.height), new Rect(r.x + first + gap, r.y, second, r.height))
                    : Arrays.asList(new Rect(r.x, r.y, r.width, first), new Rect(r.x, r.y + first + gap, r.width, second)));
        }
    }
}
