package top.cnpcplus.client.render;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Subtract in double before submitting any position to a float matrix or vertex buffer. */
public record CameraRelative(Vec3 origin) {
    public Vec3 position(Vec3 world) {
        return world.subtract(origin);
    }

    public AABB bounds(AABB world) {
        return world.move(-origin.x, -origin.y, -origin.z);
    }

    /** Forge's world view already takes camera-relative input; do not translate it again. */
    public static void viewProjection(Matrix4f view, Matrix4f projection, Matrix4f forward, Matrix4f inverse) {
        forward.set(projection).mul(view);
        inverse.set(forward).invert();
    }
}
