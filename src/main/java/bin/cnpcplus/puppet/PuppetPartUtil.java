package bin.cnpcplus.puppet;

/**
 * Shared reflection helpers for early Layer mixins (no CNPC compile deps).
 * Reads cnpcplusOffset and cnpcplusScale public fields from MixinPuppetPartConfig.
 */
public final class PuppetPartUtil {
    private PuppetPartUtil() {}

    /**
     * @return [ox,oy,oz, rxDeg,ryDeg,rzDeg, sx,sy,sz] or null if identity/disabled
     * offsets already scaled by 0.0625
     */
    public static float[] partTransform(Object part) {
        if (part == null) return null;
        try {
            if (part.getClass().getField("disabled").getBoolean(part)) return null;
            float rx = part.getClass().getField("rotationX").getFloat(part);
            float ry = part.getClass().getField("rotationY").getFloat(part);
            float rz = part.getClass().getField("rotationZ").getFloat(part);

            float ox = 0, oy = 0, oz = 0, sx = 1, sy = 1, sz = 1;
            // Prefer public mixin fields (reliable across classloaders)
            ox = getFloatField(part, "cnpcplusOffsetX", ox);
            oy = getFloatField(part, "cnpcplusOffsetY", oy);
            oz = getFloatField(part, "cnpcplusOffsetZ", oz);
            sx = getFloatField(part, "cnpcplusScaleX", sx);
            sy = getFloatField(part, "cnpcplusScaleY", sy);
            sz = getFloatField(part, "cnpcplusScaleZ", sz);

            // Fallback: interface methods if fields not visible
            if (ox == 0 && oy == 0 && oz == 0) {
                try {
                    if (Class.forName("bin.cnpcplus.puppet.PartConfigAccessor").isInstance(part)) {
                        ox = ((Float) part.getClass().getMethod("cnpcplus$getOffsetX").invoke(part)).floatValue();
                        oy = ((Float) part.getClass().getMethod("cnpcplus$getOffsetY").invoke(part)).floatValue();
                        oz = ((Float) part.getClass().getMethod("cnpcplus$getOffsetZ").invoke(part)).floatValue();
                        sx = ((Float) part.getClass().getMethod("cnpcplus$getScaleX").invoke(part)).floatValue();
                        sy = ((Float) part.getClass().getMethod("cnpcplus$getScaleY").invoke(part)).floatValue();
                        sz = ((Float) part.getClass().getMethod("cnpcplus$getScaleZ").invoke(part)).floatValue();
                    }
                } catch (Throwable ignored) {
                }
            }

            if (ox == 0 && oy == 0 && oz == 0 && rx == 0 && ry == 0 && rz == 0
                    && sx == 1 && sy == 1 && sz == 1) {
                return null;
            }
            return new float[]{
                    ox * 0.0625f, oy * 0.0625f, oz * 0.0625f,
                    rx * 180.0f, ry * 180.0f, rz * 180.0f,
                    sx, sy, sz
            };
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object equipPart(Object entity, String getter) {
        try {
            Class<?> customNpc = Class.forName("noppes.npcs.entity.EntityCustomNpc");
            if (!customNpc.isInstance(entity)) return null;
            Object advanced = customNpc.getField("advanced").get(entity);
            if (advanced == null) return null;
            if (advanced.getClass().getField("job").getInt(advanced) != 9) return null;
            Object job = customNpc.getField("jobInterface").get(entity);
            if (job == null) return null;
            if (!Class.forName("bin.cnpcplus.puppet.JobPuppetAccessor").isInstance(job)) return null;
            return job.getClass().getMethod(getter).invoke(job);
        } catch (Throwable t) {
            return null;
        }
    }

    private static float getFloatField(Object o, String name, float def) {
        try {
            return o.getClass().getField(name).getFloat(o);
        } catch (Throwable t) {
            // try declared (in case not public in some loaders)
            try {
                java.lang.reflect.Field f = o.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.getFloat(o);
            } catch (Throwable t2) {
                return def;
            }
        }
    }
}

