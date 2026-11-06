package top.cnpcplus.wand.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.wand.WandTools;

/** Client-only, terrain-following white route and arrowheads. */
public final class WandPathOverlay {
    private WandPathOverlay() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(WandTools.showsPaths(mc.player.getMainHandItem())
                || WandTools.showsPaths(mc.player.getOffhandItem()))) return;
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var lines = buffers.getBuffer(RenderType.lines());
        for (var npc : mc.level.getEntitiesOfClass(EntityNPCInterface.class, mc.player.getBoundingBox().inflate(30),
                entity -> WandTools.inRange(entity.distanceToSqr(mc.player)))) {
            var route = npc.ais.getMovingPath();
            if (route.size() < 2) continue;
            int edges = route.size() - (npc.ais.getMovingPathType() == 0 ? 0 : 1);
            for (int i = 0; i < edges; i++) {
                int[] a = route.get(i), b = route.get((i + 1) % route.size());
                Vec3 start = new Vec3(a[0] + .5, a[1], a[2] + .5);
                Vec3 end = new Vec3(b[0] + .5, b[1], b[2] + .5);
                double length = start.distanceTo(end);
                if (length < .01) continue;
                int steps = Math.min(512, Math.max(1, (int)Math.ceil(length * 2)));
                Vec3 previous = ground(start, npc);
                for (int n = 1; n <= steps; n++) {
                    Vec3 point = start.lerp(end, (double)n / steps);
                    Vec3 next = ground(point, npc);
                    if (point.distanceToSqr(mc.player.position()) <= WandTools.RANGE_SQUARED) {
                        line(pose, lines, previous, next);
                        if (n % 6 == 0 || n == steps) {
                            Vec3 direction = new Vec3(end.x - start.x, 0, end.z - start.z).normalize();
                            Vec3 rear = point.subtract(direction.scale(.38));
                            Vec3 side = new Vec3(-direction.z, 0, direction.x).scale(.2);
                            line(pose, lines, next, ground(rear.add(side), npc));
                            line(pose, lines, next, ground(rear.subtract(side), npc));
                        }
                    }
                    previous = next;
                }
            }
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static Vec3 ground(Vec3 point, EntityNPCInterface npc) {
        var hit = npc.level().clip(new ClipContext(point.add(0, 1.5, 0), point.add(0, -4, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? point.add(0, .035, 0) : hit.getLocation().add(0, .035, 0);
    }

    private static void line(PoseStack pose, VertexConsumer lines, Vec3 a, Vec3 b) {
        Vec3 normal = b.subtract(a).normalize();
        var p = pose.last();
        lines.addVertex(p.pose(), (float)a.x, (float)a.y, (float)a.z).setColor(255, 255, 255, 255)
                .setNormal(p, (float)normal.x, (float)normal.y, (float)normal.z);
        lines.addVertex(p.pose(), (float)b.x, (float)b.y, (float)b.z).setColor(255, 255, 255, 255)
                .setNormal(p, (float)normal.x, (float)normal.y, (float)normal.z);
    }
}
