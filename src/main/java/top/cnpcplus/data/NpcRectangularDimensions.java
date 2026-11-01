package top.cnpcplus.data;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;

/** Vanilla width stays conservative for navigation/entity lookup; real AABB keeps X/Z independent. */
public final class NpcRectangularDimensions extends EntityDimensions {
    public final float xWidth;
    public final float zLength;

    public NpcRectangularDimensions(float xWidth, float zLength, float height) {
        super(Math.max(xWidth, zLength), height, false);
        this.xWidth = xWidth;
        this.zLength = zLength;
    }

    @Override
    public AABB makeBoundingBox(double x, double y, double z) {
        return new AABB(x - xWidth / 2.0, y, z - zLength / 2.0,
                x + xWidth / 2.0, y + height, z + zLength / 2.0);
    }

    @Override
    public EntityDimensions scale(float horizontal, float vertical) {
        return horizontal == 1 && vertical == 1 ? this
                : new NpcRectangularDimensions(xWidth * horizontal, zLength * horizontal, height * vertical);
    }
}
