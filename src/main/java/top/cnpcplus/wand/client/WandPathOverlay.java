package top.cnpcplus.wand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.client.render.CameraRelative;
import top.cnpcplus.wand.WandTools;

/** Client-only, terrain-following gold route ribbons. Never loads server chunks. */
public final class WandPathOverlay {
    private static final int OUTLINE = 0xc029241d;
    private static final int ROUTE = 0xffffd34d;
    private static final double OUTLINE_WIDTH = .045;
    private static final double ROUTE_WIDTH = .032;
    private WandPathOverlay() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(WandTools.showsPaths(mc.player.getMainHandItem())
                || WandTools.showsPaths(mc.player.getOffhandItem()))) return;
        var pose = event.getPoseStack();
        var camera = new CameraRelative(event.getCamera().getPosition());
        var buffers = mc.renderBuffers().bufferSource();
        var routeBuffer = buffers.getBuffer(RenderType.debugQuads());
        for (var npc : mc.level.getEntitiesOfClass(EntityNPCInterface.class, mc.player.getBoundingBox().inflate(30),
                entity -> WandTools.inRange(entity.distanceToSqr(mc.player)))) {
            var route = npc.ais.getMovingPath();
            if (route.size() < 2) continue;
            boolean closed = npc.ais.getMovingPathType() == 0;
            int edges = route.size() - (closed ? 0 : 1);
            for (int i = 0; i < route.size(); i++) {
                if (!isTurn(route, i, closed)) continue;
                int[] point = route.get(i);
                Vec3 center = ground(new Vec3(point[0] + .5, point[1], point[2] + .5), npc);
                if (center.distanceToSqr(mc.player.position()) <= WandTools.RANGE_SQUARED) waypoint(pose, routeBuffer, camera.position(center));
            }
            for (int i = 0; i < edges; i++) {
                int[] a = route.get(i), b = route.get((i + 1) % route.size());
                Vec3 start = new Vec3(a[0] + .5, a[1], a[2] + .5);
                Vec3 end = new Vec3(b[0] + .5, b[1], b[2] + .5);
                double length = start.distanceTo(end);
                if (length < .01) continue;
                int steps = Math.min(512, Math.max(1, (int)Math.ceil(length * 2)));
                int arrowSpacing = Math.max(16, (int)Math.round(8.0 * steps / length));
                int arrowPhase = arrowSpacing / 2;
                Vec3 previous = ground(start, npc);
                for (int n = 1; n <= steps; n++) {
                    Vec3 point = start.lerp(end, (double)n / steps);
                    Vec3 next = ground(point, npc);
                    if (point.distanceToSqr(mc.player.position()) <= WandTools.RANGE_SQUARED) {
                        route(pose, routeBuffer, camera.position(previous), camera.position(next));
                        if (n >= arrowPhase && (n - arrowPhase) % arrowSpacing == 0 && n <= steps - arrowPhase) {
                            Vec3 direction = new Vec3(end.x - start.x, 0, end.z - start.z).normalize();
                            if (direction.lengthSqr() > 1.0e-8) {
                                Vec3 rear = point.subtract(direction.scale(.28));
                                Vec3 side = new Vec3(-direction.z, 0, direction.x).scale(.13);
                                Vec3 tip = camera.position(next);
                                route(pose, routeBuffer, tip, camera.position(ground(rear.add(side), npc)));
                                route(pose, routeBuffer, tip, camera.position(ground(rear.subtract(side), npc)));
                            }
                        }
                    }
                    previous = next;
                }
            }
        }
        buffers.endBatch(RenderType.debugQuads());
    }

    private static boolean isTurn(java.util.List<int[]> route, int index, boolean closed) {
        if (!closed && (index == 0 || index == route.size() - 1)) return true;
        if (!closed && route.size() < 3) return false;
        int[] previous = route.get((index + route.size() - 1) % route.size());
        int[] current = route.get(index);
        int[] next = route.get((index + 1) % route.size());
        double ax = current[0] - previous[0], ay = current[1] - previous[1], az = current[2] - previous[2];
        double bx = next[0] - current[0], by = next[1] - current[1], bz = next[2] - current[2];
        double lengths = Math.sqrt((ax * ax + ay * ay + az * az) * (bx * bx + by * by + bz * bz));
        return lengths == 0 || (ax * bx + ay * by + az * bz) / lengths < .94;
    }

    private static Vec3 ground(Vec3 point, EntityNPCInterface npc) {
        var hit = npc.level().clip(new ClipContext(point.add(0, 1.5, 0), point.add(0, -4, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? point.add(0, .035, 0) : hit.getLocation().add(0, .035, 0);
    }

    private static void route(PoseStack pose, VertexConsumer buffer, Vec3 a, Vec3 b) {
        routeStrip(pose, buffer, a, b, OUTLINE_WIDTH, OUTLINE);
        Vec3 lift = new Vec3(0, .004, 0);
        routeStrip(pose, buffer, a.add(lift), b.add(lift), ROUTE_WIDTH, ROUTE);
    }

    private static void routeStrip(PoseStack pose, VertexConsumer buffer, Vec3 a, Vec3 b, double width, int color) {
        Vec3 delta = b.subtract(a);
        Vec3 side = new Vec3(-delta.z, 0, delta.x);
        if (side.lengthSqr() < 1.0e-8) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width);
        quad(pose, buffer, a.add(side), b.add(side), b.subtract(side), a.subtract(side), color);
    }

    private static void waypoint(PoseStack pose, VertexConsumer buffer, Vec3 center) {
        diamond(pose, buffer, center, .11, OUTLINE);
        diamond(pose, buffer, center.add(0, .006, 0), .065, ROUTE);
    }

    private static void diamond(PoseStack pose, VertexConsumer buffer, Vec3 center, double radius, int color) {
        Vec3 middle = center.add(0, .012, 0);
        Vec3 north = middle.add(0, 0, -radius), east = middle.add(radius, 0, 0);
        Vec3 south = middle.add(0, 0, radius), west = middle.add(-radius, 0, 0);
        quad(pose, buffer, north, east, south, west, color);
    }

    private static void quad(PoseStack pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        vertex(pose, buffer, a, color); vertex(pose, buffer, b, color);
        vertex(pose, buffer, c, color); vertex(pose, buffer, d, color);
    }

    private static void vertex(PoseStack pose, VertexConsumer buffer, Vec3 point, int color) {
        var p = pose.last();
        buffer.vertex(p.pose(), (float)point.x, (float)point.y, (float)point.z)
                .color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24).endVertex();
    }
}
