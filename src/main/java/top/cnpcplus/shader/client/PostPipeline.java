package top.cnpcplus.shader.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.IntBuffer;
import java.util.*;
import static org.lwjgl.opengl.GL33C.*;

/**
 * Private script surface, scene/depth snapshots and per-instance history.
 * Only captureDepth/captureScene/present cross the game-pipeline boundary.
 * Call GPU operations inside PostRenderState; no game target is retained here.
 */
final class PostPipeline implements AutoCloseable {
    private TextureTarget scene, target;
    private final Map<UUID, TextureTarget[]> histories = new HashMap<>();
    private boolean depthReady;
    private int depthFormat;

    boolean isAllocated() { return target != null; }
    void beginFrame() { depthReady = false; }

    RenderTarget target(int width, int height) {
        if (target == null) {
            scene = new TextureTarget(width, height, true, false);
            target = new TextureTarget(width, height, true, false);
            depthFormat = 0;
        } else if (target.width != width || target.height != height) {
            scene.resize(width, height, false);
            target.resize(width, height, false);
            depthFormat = 0;
            depthReady = false;
        }
        return target;
    }

    /** WORLD_COMPLETE: read the world depth before vanilla post passes clear it. */
    void captureDepth(RenderTarget source) {
        target(source.width, source.height);
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, source.frameBufferId);
        if (glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete world depth source framebuffer: " + source.frameBufferId);
        int type = glGetFramebufferAttachmentParameteri(GL_READ_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (type != GL_TEXTURE) throw new IllegalStateException("World depth attachment is not a texture: " + type);
        int texture = glGetFramebufferAttachmentParameteri(GL_READ_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
        RenderSystem.activeTexture(GL_TEXTURE0);
        RenderSystem.bindTexture(texture);
        int format = glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_INTERNAL_FORMAT);
        if (format != depthFormat) {
            // Match the actual source, including packed depth/stencil, instead of
            // blitting into an assumed vanilla depth format or modifying the source.
            RenderSystem.bindTexture(scene.getDepthTextureId());
            boolean packed = format == GL_DEPTH24_STENCIL8 || format == GL_DEPTH32F_STENCIL8;
            int dataType = format == GL_DEPTH32F_STENCIL8 ? GL_FLOAT_32_UNSIGNED_INT_24_8_REV
                    : packed ? GL_UNSIGNED_INT_24_8 : GL_FLOAT;
            GlStateManager._texImage2D(GL_TEXTURE_2D, 0, format, scene.width, scene.height, 0,
                    packed ? GL_DEPTH_STENCIL : GL_DEPTH_COMPONENT, dataType, (IntBuffer)null);
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, scene.frameBufferId);
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Incomplete private depth snapshot framebuffer");
            depthFormat = format;
        }
        blit(source, scene, GL_DEPTH_BUFFER_BIT);
        depthReady = true;
    }

    /** AFTER_GAME_POST: copy final scene color; script passes never receive source. */
    void captureScene(RenderTarget source) {
        target(source.width, source.height);
        if (!depthReady) captureDepth(source); // Also supports the existing renderPost entry point.
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, source.frameBufferId);
        if (glCheckFramebufferStatus(GL_READ_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete scene source framebuffer: " + source.frameBufferId);
        int readBuffer = glGetInteger(GL_READ_BUFFER);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        try { blit(source, target, GL_COLOR_BUFFER_BIT); }
        finally {
            GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, source.frameBufferId);
            glReadBuffer(readBuffer);
        }
    }

    /** SCRIPT_EFFECTS: freeze this effect's scene input before it writes its private main. */
    void beginEffect(UUID token, boolean history) {
        blit(target, scene, GL_COLOR_BUFFER_BIT);
        if (history) {
            TextureTarget[] frames = histories.get(token);
            if (frames != null && (frames[0].width != target.width || frames[0].height != target.height)) {
                for (TextureTarget frame : frames) frame.destroyBuffers();
                histories.remove(token);
                frames = null;
            }
            if (frames == null) {
                frames = new TextureTarget[3];
                histories.put(token, frames);
                for (int i = 0; i < frames.length; i++) {
                    frames[i] = new TextureTarget(target.width, target.height, false, false);
                    blit(scene, frames[i], GL_COLOR_BUFFER_BIT);
                }
            }
        }
    }

    void finishEffect(UUID token, boolean advanceHistory) {
        TextureTarget[] frames = histories.get(token);
        if (frames != null && advanceHistory) {
            TextureTarget oldest = frames[2]; frames[2] = frames[1]; frames[1] = frames[0]; frames[0] = oldest;
            blit(target, oldest, GL_COLOR_BUFFER_BIT);
        }
    }

    void rollbackEffect() { blit(scene, target, GL_COLOR_BUFFER_BIT); }
    int sceneColor() { return scene.getColorTextureId(); }
    int sceneDepth() { return scene.getDepthTextureId(); }
    int history(UUID token, int index) { return histories.get(token)[index].getColorTextureId(); }

    int readTexture(int texture) {
        // Legacy auxtargets may name minecraft:main:depth; sample the immutable
        // world snapshot, never a depth attachment the next pass will clear.
        return texture == target.getDepthTextureId() ? sceneDepth() : texture;
    }

    /** BEFORE_GUI: the only game-target write is a final color-only copy. */
    void present(RenderTarget destination) {
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destination.frameBufferId);
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new IllegalStateException("Incomplete scene destination framebuffer: " + destination.frameBufferId);
        int[] drawBuffers = new int[glGetInteger(GL_MAX_DRAW_BUFFERS)];
        for (int i = 0; i < drawBuffers.length; i++) drawBuffers[i] = glGetInteger(GL_DRAW_BUFFER0 + i);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        try { blit(target, destination, GL_COLOR_BUFFER_BIT); }
        finally { glDrawBuffers(drawBuffers); }
    }

    void retainHistories(Set<UUID> tokens) {
        histories.entrySet().removeIf(entry -> {
            if (tokens.contains(entry.getKey())) return false;
            for (TextureTarget frame : entry.getValue()) if (frame != null) frame.destroyBuffers();
            return true;
        });
    }

    private static void blit(RenderTarget source, RenderTarget destination, int mask) {
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, source.width, source.height, 0, 0,
                destination.width, destination.height, mask, GL_NEAREST);
    }

    @Override public void close() {
        retainHistories(Set.of());
        if (target != null) { target.destroyBuffers(); target = null; }
        if (scene != null) { scene.destroyBuffers(); scene = null; }
        depthReady = false;
        depthFormat = 0;
    }
}
