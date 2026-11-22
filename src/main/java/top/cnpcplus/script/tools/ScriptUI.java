package top.cnpcplus.script.tools;

import java.util.List;

public final class ScriptUI {
    public record Size2(double width, double height) {}
    public record Point2(double x, double y) {}
    public record Rect(double x, double y, double width, double height) {}
    public record Insets(double top, double right, double bottom, double left) {}
    public record Constraints(double minWidth, double maxWidth, double minHeight, double maxHeight) {}
    public final Scale Scale = new Scale();
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

        private void positiveScale(double scale) {
            if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Scale must be finite and positive");
        }
    }

    public static final class Layout {
        public Rect center(Rect parent, Size2 size) { return align(parent, size, "CENTER"); }
        public Rect align(Rect parent, Size2 size, String alignment) {
            double x = parent.width - size.width;
            double y = parent.height - size.height;
            return switch (alignment) {
                case "TOP_LEFT" -> new Rect(parent.x, parent.y, size.width, size.height);
                case "TOP" -> new Rect(parent.x + x / 2, parent.y, size.width, size.height);
                case "TOP_RIGHT" -> new Rect(parent.x + x, parent.y, size.width, size.height);
                case "LEFT" -> new Rect(parent.x, parent.y + y / 2, size.width, size.height);
                case "CENTER" -> new Rect(parent.x + x / 2, parent.y + y / 2, size.width, size.height);
                case "RIGHT" -> new Rect(parent.x + x, parent.y + y / 2, size.width, size.height);
                case "BOTTOM_LEFT" -> new Rect(parent.x, parent.y + y, size.width, size.height);
                case "BOTTOM" -> new Rect(parent.x + x / 2, parent.y + y, size.width, size.height);
                case "BOTTOM_RIGHT" -> new Rect(parent.x + x, parent.y + y, size.width, size.height);
                default -> throw new IllegalArgumentException("Unknown alignment");
            };
        }
        public Rect anchor(Rect parent, Size2 size, String anchor, Point2 offset) {
            Rect rect = align(parent, size, anchor);
            return new Rect(rect.x + offset.x, rect.y + offset.y, rect.width, rect.height);
        }
        public Rect inset(Rect rect, Insets inset) {
            return new Rect(rect.x + inset.left, rect.y + inset.top,
                    Math.max(0, rect.width - inset.left - inset.right), Math.max(0, rect.height - inset.top - inset.bottom));
        }
        public Rect expand(Rect rect, Insets inset) { return inset(rect, new Insets(-inset.top, -inset.right, -inset.bottom, -inset.left)); }
        public Rect clamp(Rect rect, Rect bounds) {
            double w = Math.min(rect.width, bounds.width);
            double h = Math.min(rect.height, bounds.height);
            return new Rect(Math.max(bounds.x, Math.min(rect.x, bounds.x + bounds.width - w)),
                    Math.max(bounds.y, Math.min(rect.y, bounds.y + bounds.height - h)), w, h);
        }
        public Size2 fit(Size2 size, Constraints limits) {
            return new Size2(Math.max(limits.minWidth, Math.min(size.width, limits.maxWidth)), Math.max(limits.minHeight, Math.min(size.height, limits.maxHeight)));
        }
        public List<Rect> split(Rect rect, String direction, double ratio, double gap) {
            if (!(ratio >= 0 && ratio <= 1 && gap >= 0) || !(direction.equals("HORIZONTAL") || direction.equals("VERTICAL"))) {
                throw new IllegalArgumentException("Invalid split");
            }
            boolean horizontal = direction.equals("HORIZONTAL");
            double space = horizontal ? rect.width : rect.height;
            gap = Math.min(gap, space);
            double first = (space - gap) * ratio;
            double second = space - gap - first;
            return horizontal ? List.of(new Rect(rect.x, rect.y, first, rect.height), new Rect(rect.x + first + gap, rect.y, second, rect.height))
                    : List.of(new Rect(rect.x, rect.y, rect.width, first), new Rect(rect.x, rect.y + first + gap, rect.width, second));
        }
    }
}
