package bin.cnpcplus.script.tools;

/** Immutable coordinates and vector operations in blocks. */
public final class Position {
    private final double x, y, z;
    public Position(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public Position add(Position other) { return new Position(x + other.x, y + other.y, z + other.z); }
    public Position subtract(Position other) { return new Position(x - other.x, y - other.y, z - other.z); }
    public Position multiply(Position other) { return new Position(x * other.x, y * other.y, z * other.z); }
    public Position divide(double divisor) { return scale(1 / divisor); }
    public Position scale(double amount) { return new Position(x * amount, y * amount, z * amount); }
    public Position negate() { return scale(-1); }
    public double dot(Position other) { return x * other.x + y * other.y + z * other.z; }
    public Position cross(Position other) { return new Position(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x); }
    public double lengthSquared() { return dot(this); }
    public double length() { return Math.hypot(Math.hypot(x, y), z); }
    public double distanceSquared(Position other) { return subtract(other).lengthSquared(); }
    public double distance(Position other) { return subtract(other).length(); }
    public boolean isFinite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    public boolean isZero() { return x == 0 && y == 0 && z == 0; }
    public Position normalize() {
        double max = Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z));
        if (!isFinite() || max == 0) return new Position(0, 0, 0);
        Position scaled = new Position(x / max, y / max, z / max);
        return scaled.divide(scaled.length());
    }
    public OperationResult<Position> tryNormalize() {
        return isFinite() && !isZero() ? OperationResult.success(normalize()) : OperationResult.failure("MATH", "INVALID_VECTOR");
    }
    public Position project(Position onto) { Position axis = onto.normalize(); return axis.scale(dot(axis)); }
    public Position reject(Position onto) { return subtract(project(onto)); }
    public double angleTo(Position other) {
        if (!isFinite() || !other.isFinite() || isZero() || other.isZero()) throw new IllegalArgumentException("Expected finite, nonzero vectors");
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, normalize().dot(other.normalize())))));
    }
    public Position rotate(Position axis, double degrees) {
        Position unit = axis.normalize();
        if (unit.isZero()) return this;
        double radians = Math.toRadians(degrees), cosine = Math.cos(radians);
        return scale(cosine).add(unit.cross(this).scale(Math.sin(radians))).add(project(unit).scale(1 - cosine));
    }
    public Position lerp(Position other, double amount) { return add(other.subtract(this).scale(amount)); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof Position)) return false;
        Position p = (Position)other;
        return Double.compare(x, p.x) == 0 && Double.compare(y, p.y) == 0 && Double.compare(z, p.z) == 0;
    }
    @Override public int hashCode() { return java.util.Objects.hash(x, y, z); }
    @Override public String toString() { return "Position[x=" + x + ", y=" + y + ", z=" + z + "]"; }
}
