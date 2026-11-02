package top.cnpcplus.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.EntityUtil;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import top.cnpcplus.mixin.EntityPreviewDispatcherAccess;

/** Same native rendering, with paired stacks and restoration even when a model renderer throws. */
public final class SafeEntityPreview {
    private static long lastError;
    private SafeEntityPreview() {}

    public static boolean inventory(GuiGraphics g, int x, int y, int size, Quaternionf pose, Quaternionf camera, LivingEntity entity) {
        if (entity == null || size <= 0 || pose == null) return false;
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf oldCamera = new Quaternionf(dispatcher.cameraOrientation());
        boolean shadow = ((EntityPreviewDispatcherAccess)dispatcher).cnpcplus$renderShadow();
        PoseStack matrix = new PoseStack();
        matrix.last().pose().set(g.pose().last().pose());
        matrix.last().normal().set(g.pose().last().normal());
        boolean rendered = false;
        try {
            matrix.translate(x, y, 50);
            matrix.mulPoseMatrix(new Matrix4f().scaling(size, size, -size));
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

    public static boolean nativeDisplay(GuiGraphics g, Entity entity, int x, int y, float zoom, int rotation,
            int mx, int my, float left, float top, boolean follow, boolean riders) {
        if (entity == null || !Float.isFinite(zoom) || zoom <= 0) return false;
        float height = entity.getBbHeight(), width = entity.getBbWidth();
        if (!Float.isFinite(height) || !Float.isFinite(width) || height < 0 || width <= 0) return false;
        LivingEntity living = entity instanceof LivingEntity value ? value : null;
        EntityNPCInterface npc = entity instanceof EntityNPCInterface value ? value : null;
        float yaw = entity.getYRot(), pitch = entity.getXRot();
        float body = living == null ? 0 : living.yBodyRot, head = living == null ? 0 : living.yHeadRot, oldHead = living == null ? 0 : living.yHeadRotO;
        int orientation = npc == null ? 0 : npc.ais.orientation, name = npc == null ? 0 : npc.display.getShowName();
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        boolean shadow = ((EntityPreviewDispatcherAccess)dispatcher).cnpcplus$renderShadow();
        Quaternionf camera = new Quaternionf(dispatcher.cameraOrientation());
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        boolean rendered = false;
        try {
            float scale = height > 2.4f ? 2 / height : 1;
            float dx = left + x - mx, dy = (top + y - 50 * scale * zoom) * (height / width) - my;
            entity.setYRot(follow ? (float)Math.atan(dx / 80) * 40 + rotation : rotation);
            entity.setXRot(follow ? -(float)Math.atan(dy / 40) * 20 : 0);
            if (living != null) living.yHeadRot = living.yHeadRotO = living.yBodyRot = entity.getYRot();
            if (npc != null) { npc.ais.orientation = (int)entity.getYRot(); npc.display.setShowName(1); }
            copyModel(npc);
            modelView.translate(0, 0, 1050);
            modelView.scale(1, 1, -1);
            RenderSystem.applyModelViewMatrix();
            PoseStack matrix = new PoseStack();
            matrix.translate(left + x, top + y, 0);
            matrix.scale(30 * scale * zoom, 30 * scale * zoom, 30 * scale * zoom);
            matrix.mulPose(Axis.YP.rotationDegrees(180));
            matrix.mulPose(Axis.ZP.rotationDegrees(180));
            matrix.mulPose(Axis.YN.rotationDegrees(rotation));
            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> {
                if (!riders) dispatcher.render(entity, 0, 0, 0, 0, 1, matrix, g.bufferSource(), 15728880);
                else entity.getSelfAndPassengers().forEach(passenger -> {
                    double offset = 0;
                    for (Entity current = passenger; current.getVehicle() != null; current = current.getVehicle()) offset += current.getMyRidingOffset();
                    dispatcher.render(passenger, 0, offset, 0, 0, 1, matrix, g.bufferSource(), 15728880);
                });
            });
            rendered = true;
        } catch (RuntimeException | LinkageError e) { failure(e); }
        finally {
            try { g.flush(); }
            catch (RuntimeException | LinkageError e) { rendered = false; failure(e); }
            finally {
                dispatcher.setRenderShadow(shadow);
                dispatcher.overrideCameraOrientation(camera);
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
                Lighting.setupFor3DItems();
                entity.setYRot(yaw); entity.setXRot(pitch);
                if (living != null) { living.yBodyRot = body; living.yHeadRot = head; living.yHeadRotO = oldHead; }
                if (npc != null) { npc.ais.orientation = orientation; npc.display.setShowName(name); }
                try { copyModel(npc); } catch (RuntimeException | LinkageError e) { failure(e); }
            }
        }
        return rendered;
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
