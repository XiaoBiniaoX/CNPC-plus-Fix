package top.cnpcplus.questtrigger.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import top.cnpcplus.client.render.CameraRelative;

public class QuestTriggerOverlay {

    private static final float R = 1.0F, G = 0.2F, B = 0.2F, A = 1.0F;

    private static BlockPos pos;
    private static int areaMode;
    private static int sizeX, sizeY, sizeZ;
    private static int radius;
    private static boolean visible;

    public static boolean isVisible() {
        return visible;
    }

    public static void show(BlockPos pos, int areaMode, int sizeX, int sizeY, int sizeZ, int radius) {
        updateData(pos, areaMode, sizeX, sizeY, sizeZ, radius);
        visible = true;
    }

    public static void updateData(BlockPos pos, int areaMode, int sizeX, int sizeY, int sizeZ, int radius) {
        QuestTriggerOverlay.pos = pos;
        QuestTriggerOverlay.areaMode = areaMode;
        QuestTriggerOverlay.sizeX = sizeX;
        QuestTriggerOverlay.sizeY = sizeY;
        QuestTriggerOverlay.sizeZ = sizeZ;
        QuestTriggerOverlay.radius = radius;
    }

    public static void hide() {
        visible = false;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!visible || pos == null) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.screen instanceof GuiQuestTrigger gui) {
            gui.syncOverlay();
        }
        if (mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 65536) return;

        try {
            PoseStack pose = event.getPoseStack();
            var camera = new CameraRelative(event.getCamera().getPosition());
            MultiBufferSource.BufferSource src = mc.renderBuffers().bufferSource();

            VertexConsumer vc = src.getBuffer(RenderType.lines());
            if (areaMode == 0) {
                AABB box = new AABB(pos).inflate(Math.max(0, sizeX), Math.max(0, sizeY), Math.max(0, sizeZ));
                LevelRenderer.renderLineBox(pose, vc, camera.bounds(box), R, G, B, A);
            } else {
                double r = Math.max(0, radius) + 0.5;
                Vec3 center = camera.position(Vec3.atCenterOf(pos));
                circle(pose, vc, center.x, center.y, center.z, r, 1, 0, 0);
                circle(pose, vc, center.x, center.y, center.z, r, 0, 1, 0);
                circle(pose, vc, center.x, center.y, center.z, r, 0, 0, 1);
            }
            src.endBatch(RenderType.lines());

        } catch (Throwable t) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("任务触发器区域绘制失败", t);
        }
    }

    private static void circle(PoseStack pose, VertexConsumer vc, double cx, double cy, double cz, double r,
                               int axisX, int axisY, int axisZ) {
        int segments = 32;
        double px = 0, py = 0, pz = 0;
        boolean hasPrev = false;
        for (int i = 0; i <= segments; i++) {
            double a = Math.PI * 2 * i / segments;
            double x = cx + axisX * r * Math.cos(a) + axisZ * r * Math.sin(a);
            double y = cy + axisY * r * Math.sin(a);
            double z = cz + axisZ * r * Math.cos(a) + axisX * r * Math.sin(a);
            if (hasPrev) {
                line(pose, vc, px, py, pz, x, y, z);
            }
            px = x;
            py = y;
            pz = z;
            hasPrev = true;
        }
    }

    private static void line(PoseStack pose, VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1) {
        vc.vertex(pose.last().pose(), (float) x0, (float) y0, (float) z0).color(R, G, B, A).normal(0, 0, 1).endVertex();
        vc.vertex(pose.last().pose(), (float) x1, (float) y1, (float) z1).color(R, G, B, A).normal(0, 0, 1).endVertex();
    }
}
