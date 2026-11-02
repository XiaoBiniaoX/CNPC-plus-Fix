package top.cnpcplus.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.EntityUtil;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import top.cnpcplus.mixin.EntityPreviewDispatcherAccess;

/** Detached previews: no native unpaired pop, world insertion or ticking; restore renderer state on failure. */
public final class SafeEntityPreview {
    private static long lastError;
    private SafeEntityPreview() {}

    public static boolean inventory(GuiGraphics g, int x, int y, int size, Quaternionf pose, Quaternionf camera, LivingEntity entity) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf oldCamera = new Quaternionf(dispatcher.cameraOrientation());
        boolean shadow = ((EntityPreviewDispatcherAccess)dispatcher).cnpcplus$renderShadow();
        PoseStack matrix = new PoseStack();
        matrix.last().pose().set(g.pose().last().pose());
        matrix.last().normal().set(g.pose().last().normal());
        boolean rendered = false;
        try {
            matrix.translate(x, y, 50);
            matrix.mulPose(new Matrix4f().scaling(size, size, -size));
            matrix.mulPose(pose);
            Lighting.setupForEntityInInventory();
            if (camera != null) dispatcher.overrideCameraOrientation(new Quaternionf(camera).conjugate());
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0, 1, matrix, g.bufferSource(), 15728880));
            rendered = true;
        } catch (RuntimeException | LinkageError e) { failure(e); }
        finally {
            try { g.flush(); }
            catch (RuntimeException | LinkageError e) { rendered = false; failure(e); }
            finally {
                dispatcher.setRenderShadow(shadow);
                dispatcher.overrideCameraOrientation(oldCamera);
                Lighting.setupFor3DItems();
            }
        }
        return rendered;
    }

    public static boolean nativeDisplay(GuiGraphics g, EntityNPCInterface npc, int x, int y, float zoom, int rotation,
            int mx, int my, float left, float top, boolean follow, boolean riders) {
        float yaw = npc.getYRot(), pitch = npc.getXRot(), body = npc.yBodyRot, head = npc.yHeadRot, oldHead = npc.yHeadRotO;
        int orientation = npc.ais.orientation, name = npc.display.getShowName();
        try {
            float height = npc.getBbHeight();
            float scale = height > 2.4f ? 2 / height : 1;
            float dx = left + x - mx, dy = top + y - 50 * scale * zoom - my;
            npc.setYRot(follow ? (float)Math.atan(dx / 80) * 40 + rotation : rotation);
            npc.setXRot(follow ? -(float)Math.atan(dy / 40) * 20 : 0);
            npc.yHeadRot = npc.yHeadRotO = npc.yBodyRot = npc.getYRot();
            npc.ais.orientation = (int)npc.getYRot(); npc.display.setShowName(1);
            copyModel(npc);
            Quaternionf pose = Axis.YP.rotationDegrees(180).mul(Axis.ZP.rotationDegrees(180)).mul(Axis.YN.rotationDegrees(rotation));
            return inventory(g, (int)left + x, (int)top + y, Math.max(1, (int)(30 * scale * zoom)), pose, new Quaternionf(), npc);
        } finally {
            npc.setYRot(yaw); npc.setXRot(pitch); npc.yBodyRot = body; npc.yHeadRot = head; npc.yHeadRotO = oldHead;
            npc.ais.orientation = orientation; npc.display.setShowName(name);
            copyModel(npc);
        }
    }

    private static void copyModel(EntityNPCInterface npc) {
        if (npc instanceof EntityCustomNpc custom) {
            LivingEntity model = custom.modelData.getEntity(custom);
            if (model != null) EntityUtil.Copy(npc, model);
        }
    }
    private static void failure(Throwable error) {
        long now = System.nanoTime();
        if (lastError == 0 || now - lastError > java.util.concurrent.TimeUnit.SECONDS.toNanos(10)) {
            lastError = now;
            org.slf4j.LoggerFactory.getLogger("cnpcplus").warn("[preview] model rendering failed; GUI state restored", error);
        }
    }
}
