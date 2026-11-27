package top.cnpcplus.questtrigger.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class QuestTriggerOverlay {
    private static final float R = 1, G = .2F, B = .2F, A = 1;
    private static BlockPos pos;
    private static int areaMode, sizeX, sizeY, sizeZ, radius;
    private static boolean visible;
    private QuestTriggerOverlay() {}

    public static boolean isVisible() { return visible; }
    public static void show(BlockPos position, int mode, int x, int y, int z, int r) {
        updateData(position, mode, x, y, z, r);
        visible = true;
    }
    public static void updateData(BlockPos position, int mode, int x, int y, int z, int r) {
        pos = position; areaMode = mode; sizeX = x; sizeY = y; sizeZ = z; radius = r;
    }
    public static void hide() { visible = false; }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!visible || pos == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.screen instanceof GuiQuestTrigger gui) gui.syncOverlay();
        if (mc.player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 65536) return;
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        if (areaMode == 0) {
            AABB box = new AABB(pos).inflate(Math.max(0, sizeX), Math.max(0, sizeY), Math.max(0, sizeZ));
            LevelRenderer.renderLineBox(pose, vc, box.move(-camera.x, -camera.y, -camera.z), R, G, B, A);
        } else {
            double r = Math.max(0, radius) + .5;
            Vec3 center = Vec3.atCenterOf(pos).subtract(camera);
            circle(pose, vc, center, r, 0);
            circle(pose, vc, center, r, 1);
            circle(pose, vc, center, r, 2);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void circle(PoseStack pose, VertexConsumer vc, Vec3 center, double radius, int axis) {
        Vec3 previous = null;
        for (int i = 0; i <= 32; i++) {
            double a = Math.PI * 2 * i / 32, c = radius * Math.cos(a), s = radius * Math.sin(a);
            Vec3 point = center.add(axis == 2 ? 0 : c, axis == 1 ? 0 : s, axis == 0 ? 0 : axis == 1 ? s : c);
            if (previous != null) {
                vc.addVertex(pose.last().pose(), (float)previous.x, (float)previous.y, (float)previous.z).setColor(R, G, B, A).setNormal(pose.last(), 0, 0, 1);
                vc.addVertex(pose.last().pose(), (float)point.x, (float)point.y, (float)point.z).setColor(R, G, B, A).setNormal(pose.last(), 0, 0, 1);
            }
            previous = point;
        }
    }
}
