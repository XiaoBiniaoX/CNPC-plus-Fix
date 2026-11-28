package bin.cnpcplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import noppes.npcs.client.EntityUtil;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Fixed-pose previews, matching the modern selectors without changing the displayed entity. */
final class SafeEntityPreview {
    private static final FloatBuffer FEEDBACK = BufferUtils.createFloatBuffer(262144);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);

    private SafeEntityPreview() { }

    static void draw(EntityLivingBase entity, int frameX, int frameY, int frameWidth, int frameHeight,
            float zoom, int rotation) {
        EntityNPCInterface npc = entity instanceof EntityNPCInterface ? (EntityNPCInterface)entity : null;
        EntityLivingBase model = entity instanceof EntityCustomNpc ? ((EntityCustomNpc)entity).modelData.getEntity(npc) : null;
        float yaw = entity.rotationYaw, oldYaw = entity.prevRotationYaw;
        float pitch = entity.rotationPitch, oldPitch = entity.prevRotationPitch;
        float body = entity.renderYawOffset, oldBody = entity.prevRenderYawOffset;
        float head = entity.rotationYawHead, oldHead = entity.prevRotationYawHead;
        int orientation = npc == null ? 0 : npc.ais.orientation, name = npc == null ? 0 : npc.display.getShowName();
        RenderManager manager = Minecraft.getMinecraft().getRenderManager();
        float camera = manager.playerViewY;
        boolean shadow = manager.isRenderShadow(), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.pushMatrix();
        try {
            entity.prevRotationYaw = entity.rotationYaw = rotation;
            entity.prevRotationPitch = entity.rotationPitch = 0;
            entity.prevRenderYawOffset = entity.renderYawOffset = rotation;
            entity.prevRotationYawHead = entity.rotationYawHead = rotation;
            if (npc != null) { npc.ais.orientation = rotation; npc.display.setShowName(1); }
            if (model != null) EntityUtil.Copy(entity, model);
            GlStateManager.enableDepth();
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.enableColorMaterial();
            RenderHelper.enableGUIStandardItemLighting();
            manager.playerViewY = 180;
            manager.setRenderShadow(false);
            float[] bounds = measure(entity, manager, rotation);
            float scale = 30 * zoom;
            float x = frameX + frameWidth / 2F;
            float y = frameY + frameHeight / 2F;
            if (bounds != null) {
                scale = Math.min(scale, Math.min((frameWidth - 8) / (bounds[2] - bounds[0]),
                        (frameHeight - 8) / (bounds[3] - bounds[1])));
                x -= (bounds[0] + bounds[2]) / 2 * scale;
                y -= (bounds[1] + bounds[3]) / 2 * scale;
            }
            render(entity, manager, x, y, scale, rotation);
        } finally {
            manager.setRenderShadow(shadow);
            manager.playerViewY = camera;
            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            if (!depth) GlStateManager.disableDepth();
            entity.rotationYaw = yaw; entity.prevRotationYaw = oldYaw;
            entity.rotationPitch = pitch; entity.prevRotationPitch = oldPitch;
            entity.renderYawOffset = body; entity.prevRenderYawOffset = oldBody;
            entity.rotationYawHead = head; entity.prevRotationYawHead = oldHead;
            if (npc != null) { npc.ais.orientation = orientation; npc.display.setShowName(name); }
            if (model != null) EntityUtil.Copy(entity, model);
        }
    }

    /** Measure rendered geometry at unit scale, independently of hitboxes and the GUI's clipping. */
    private static float[] measure(EntityLivingBase entity, RenderManager manager, int rotation) {
        FEEDBACK.clear();
        VIEWPORT.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        GL11.glFeedbackBuffer(GL11.GL_3D, FEEDBACK);
        GL11.glPushAttrib(GL11.GL_SCISSOR_BIT);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(-4096, 4096, 4096, -4096, -4096, 4096);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GL11.glRenderMode(GL11.GL_FEEDBACK);
        int count;
        try {
            render(entity, manager, 0, 0, 1, rotation);
        } finally {
            count = GL11.glRenderMode(GL11.GL_RENDER);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
        }
        if (count <= 0) return null;
        FEEDBACK.limit(count);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        while (FEEDBACK.hasRemaining()) {
            int token = (int)FEEDBACK.get();
            int vertices;
            if (token == GL11.GL_POLYGON_TOKEN) vertices = (int)FEEDBACK.get();
            else if (token == GL11.GL_POINT_TOKEN) vertices = 1;
            else if (token == GL11.GL_LINE_TOKEN || token == GL11.GL_LINE_RESET_TOKEN) vertices = 2;
            else if (token == GL11.GL_PASS_THROUGH_TOKEN) { FEEDBACK.get(); continue; }
            else if (token == GL11.GL_BITMAP_TOKEN || token == GL11.GL_DRAW_PIXEL_TOKEN || token == GL11.GL_COPY_PIXEL_TOKEN) {
                FEEDBACK.position(FEEDBACK.position() + 3);
                continue;
            } else return null;
            for (int i = 0; i < vertices; i++) {
                float px = (FEEDBACK.get() - VIEWPORT.get(0)) * 8192 / VIEWPORT.get(2) - 4096;
                float py = 4096 - (FEEDBACK.get() - VIEWPORT.get(1)) * 8192 / VIEWPORT.get(3);
                FEEDBACK.get();
                minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                minY = Math.min(minY, py); maxY = Math.max(maxY, py);
            }
        }
        return minX < maxX && minY < maxY ? new float[]{minX, minY, maxX, maxY} : null;
    }

    private static void render(EntityLivingBase entity, RenderManager manager,
            float x, float y, float scale, int rotation) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y, 50);
            GlStateManager.scale(-scale, scale, scale);
            GlStateManager.rotate(180, 0, 0, 1);
            GlStateManager.rotate(-rotation, 0, 1, 0);
            // RenderLivingBase re-enables the world lightmap after the measurement pass.
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            manager.renderEntity(entity, 0, 0, 0, 0, 1, false);
        } finally { GlStateManager.popMatrix(); }
    }
}
