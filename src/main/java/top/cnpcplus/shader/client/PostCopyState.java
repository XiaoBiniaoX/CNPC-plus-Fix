package top.cnpcplus.shader.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import static org.lwjgl.opengl.GL33C.*;

/** A depth blit changes framebuffer/scissor state, not programs, VAOs or blending. */
final class PostCopyState implements AutoCloseable {
    private final RenderTarget main;
    private final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    private final int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
    private final boolean scissor = glIsEnabled(GL_SCISSOR_TEST);
    private final int active = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int texture;

    PostCopyState(RenderTarget main) {
        this.main = main;
        // Only unit 0 is used for depth format inspection/storage changes.
        PostRenderState.activeTexture(GL_TEXTURE0);
        texture = glGetInteger(GL_TEXTURE_BINDING_2D);
        RenderSystem.bindTexture(texture);
        RenderSystem.disableScissor();
        glDisable(GL_SCISSOR_TEST);
    }

    @Override public void close() {
        PostRenderState.activeTexture(GL_TEXTURE0);
        RenderSystem.bindTexture(texture);
        glBindTexture(GL_TEXTURE_2D, texture);
        PostRenderState.activeTexture(active);
        if (scissor) {
            // The box was never changed; only restore the enable flag.
            GlStateManager._enableScissorTest();
            glEnable(GL_SCISSOR_TEST);
        } else {
            RenderSystem.disableScissor();
            glDisable(GL_SCISSOR_TEST);
        }
        if (draw == main.frameBufferId) main.bindWrite(false);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
    }
}
