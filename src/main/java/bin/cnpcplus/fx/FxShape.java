package bin.cnpcplus.fx;

import net.minecraft.nbt.NBTTagCompound;

public final class FxShape {
    public static final String[] KEYS = {"Radius", "RotSpeed", "Rotation", "RotSpeedX", "RotationX",
            "RotSpeedZ", "RotationZ", "InsetAmt", "Duration", "Density", "Ox", "Oy", "Oz"};
    public ShapeType type = ShapeType.CIRCLE;
    public boolean inset;
    public final double[] values = {1, 0, 0, 0, 0, 0, 0, 0, 60, 1, 0, .5, 0};

    public void set(int field, double value) {
        if (!Double.isFinite(value)) value = field == 8 ? 60 : field == 9 ? 1 : 0;
        double min = field == 8 ? 1 : field == 9 ? .01 : field == 2 || field == 4 || field == 6 ? -360 : -100;
        double max = field == 8 ? 1200 : field == 2 || field == 4 || field == 6 ? 360 : 100;
        values[field] = Math.max(min, Math.min(max, value));
        if (field == 8) values[field] = Math.round(values[field]);
    }

    public int duration() { return (int) values[8]; }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Type", type.name());
        tag.setBoolean("Inset", inset);
        for (int i = 0; i < KEYS.length; i++) {
            if (i == 8) tag.setInteger(KEYS[i], duration());
            else tag.setDouble(KEYS[i], values[i]);
        }
        return tag;
    }

    public static FxShape fromNBT(NBTTagCompound tag) {
        FxShape shape = new FxShape();
        try { shape.type = ShapeType.valueOf(tag.getString("Type")); }
        catch (IllegalArgumentException ignored) { }
        shape.inset = tag.getBoolean("Inset");
        for (int i = 0; i < KEYS.length; i++) if (tag.hasKey(KEYS[i], 99)) shape.set(i, tag.getDouble(KEYS[i]));
        return shape;
    }

    public double[] points() {
        double[] out = type.sample((int) Math.max(1, Math.min(256, Math.round(values[9] * 16))), 42);
        for (int i = 0; i < out.length; i += 2) {
            double length = Math.hypot(out[i], out[i + 1]);
            double shift = (inset ? -1 : 1) * values[7] * .1;
            double scale = Math.abs(values[0]) * (length < 1e-6 ? 1 : 1 + shift / length);
            out[i] *= scale;
            out[i + 1] *= scale;
        }
        return out;
    }

    /** X → Y → Z rotation, speeds in radians/tick; offsets in world units. */
    public double[] transform(double x, double z, long age) {
        double ax = Math.toRadians(values[4]) + values[3] * age;
        double ay = Math.toRadians(values[2]) + values[1] * age;
        double az = Math.toRadians(values[6]) + values[5] * age;
        double y1 = -z * Math.sin(ax), z1 = z * Math.cos(ax);
        double x2 = x * Math.cos(ay) - z1 * Math.sin(ay), z2 = x * Math.sin(ay) + z1 * Math.cos(ay);
        return new double[]{x2 * Math.cos(az) - y1 * Math.sin(az) + values[10],
                x2 * Math.sin(az) + y1 * Math.cos(az) + values[11], z2 + values[12]};
    }
}
