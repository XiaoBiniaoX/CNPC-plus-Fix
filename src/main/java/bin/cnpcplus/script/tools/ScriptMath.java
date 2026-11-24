package bin.cnpcplus.script.tools;

public final class ScriptMath {
    public static final class BlockPosition {
        private final int x, y, z;
        public BlockPosition(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int x() { return x; } public int y() { return y; } public int z() { return z; }
    }
    public static final class Ray {
        private final Position origin, direction;
        public Ray(Position origin, Position direction) { this.origin = origin; this.direction = direction; }
        public Position origin() { return origin; } public Position direction() { return direction; }
    }
    public static final class Box {
        private final Position min, max;
        public Box(Position min, Position max) { this.min = min; this.max = max; }
        public Position min() { return min; } public Position max() { return max; }
    }
    public static final class LineSegment {
        private final Position start, end;
        public LineSegment(Position start, Position end) { this.start = start; this.end = end; }
        public Position start() { return start; } public Position end() { return end; }
    }
    public static final class Sphere {
        private final Position center; private final double radius;
        public Sphere(Position center, double radius) { this.center = center; this.radius = radius; }
        public Position center() { return center; } public double radius() { return radius; }
    }
    public Position position(double x, double y, double z) { return new Position(x, y, z); }
    public BlockPosition blockPosition(int x, int y, int z) { return new BlockPosition(x, y, z); }
    public Ray ray(Position start, Position direction) { return new Ray(start, direction); }
    public Box box(Position a, Position b) {
        return new Box(new Position(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())),
                new Position(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())));
    }
    public LineSegment segment(Position start, Position end) { return new LineSegment(start, end); }
    public Sphere sphere(Position center, double radius) {
        if (!Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("radius must be finite and nonnegative");
        return new Sphere(center, radius);
    }
    public double clamp(double value, double min, double max) {
        if (min > max) throw new IllegalArgumentException("min must be <= max");
        return Math.max(min, Math.min(max, value));
    }
    public double lerp(double start, double end, double amount) { return start + (end - start) * amount; }
    public double inverseLerp(double start, double end, double value) { return start == end ? 0 : (value - start) / (end - start); }
    public double mapRange(double value, double inMin, double inMax, double outMin, double outMax) { return lerp(outMin, outMax, inverseLerp(inMin, inMax, value)); }
    public double normalizeDegrees(double angle) { double rest = angle % 360; return rest < 0 ? rest + 360 : rest + 0; }
    public double wrapDegrees(double angle) { double rest = normalizeDegrees(angle); return rest >= 180 ? rest - 360 : rest; }
    public double approach(double current, double target, double maxDelta) { return current + clamp(target - current, -maxDelta, maxDelta); }
    public double smoothStep(double edge0, double edge1, double value) { double t = clamp(inverseLerp(edge0, edge1, value), 0, 1); return t * t * (3 - 2 * t); }
    public boolean containsPoint(Box box, Position point) { return closestPoint(box, point).equals(point); }
    public boolean intersects(Box first, Box second) {
        return first.min.x() <= second.max.x() && first.max.x() >= second.min.x()
                && first.min.y() <= second.max.y() && first.max.y() >= second.min.y() && first.min.z() <= second.max.z() && first.max.z() >= second.min.z();
    }
    public Position closestPoint(Box box, Position point) {
        return new Position(clamp(point.x(), box.min.x(), box.max.x()), clamp(point.y(), box.min.y(), box.max.y()), clamp(point.z(), box.min.z(), box.max.z()));
    }
    public double distanceTo(Box box, Position point) { return closestPoint(box, point).distance(point); }
    public Position closestPointOnSegment(LineSegment line, Position point) {
        Position offset = line.end.subtract(line.start);
        if (offset.isZero()) return line.start;
        return line.start.add(offset.scale(clamp(point.subtract(line.start).dot(offset) / offset.lengthSquared(), 0, 1)));
    }
    public double distanceToSegment(LineSegment line, Position point) { return closestPointOnSegment(line, point).distance(point); }
    public boolean containsPoint(Sphere sphere, Position point) { return sphere.center.distanceSquared(point) <= sphere.radius * sphere.radius; }
    public boolean intersects(Sphere first, Sphere second) { double radius = first.radius + second.radius; return first.center.distanceSquared(second.center) <= radius * radius; }
    public OperationResult<Double> intersectRayAABB(Ray ray, Box box) {
        Position direction = ray.direction.normalize();
        if (direction.isZero() || !ray.origin.isFinite()) return OperationResult.failure("MATH", "INVALID_RAY");
        double[] start = {ray.origin.x(), ray.origin.y(), ray.origin.z()}, delta = {direction.x(), direction.y(), direction.z()};
        double[] min = {box.min.x(), box.min.y(), box.min.z()}, max = {box.max.x(), box.max.y(), box.max.z()};
        double near = 0, far = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (delta[axis] == 0) { if (start[axis] < min[axis] || start[axis] > max[axis]) return OperationResult.failure("MATH", "MISS"); continue; }
            double a = (min[axis] - start[axis]) / delta[axis], b = (max[axis] - start[axis]) / delta[axis];
            near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b));
            if (near > far) return OperationResult.failure("MATH", "MISS");
        }
        return OperationResult.success(near);
    }
}
