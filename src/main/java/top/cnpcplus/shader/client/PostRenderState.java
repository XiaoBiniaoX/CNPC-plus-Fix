package top.cnpcplus.shader.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.BlendMode;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.renderer.EffectInstance;
import org.joml.Matrix4f;
import top.cnpcplus.mixin.ShaderBlendModeAccess;
import static org.lwjgl.opengl.GL33C.*;

/** Owns the state required by full-screen PostChain passes and target allocation. */
final class PostRenderState implements AutoCloseable {
    private final RenderTarget main;
    private final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
    private final int program = glGetInteger(GL_CURRENT_PROGRAM), activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
    private final int vao = glGetInteger(GL_VERTEX_ARRAY_BINDING), arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
    private final int elementBuffer = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
    private final int[] viewport = new int[4], scissorBox = new int[4], colorMask = new int[4];
    private final float[] clearColor = new float[4];
    private final double clearDepth = glGetDouble(GL_DEPTH_CLEAR_VALUE);
    private final boolean scissor = glIsEnabled(GL_SCISSOR_TEST), blend = glIsEnabled(GL_BLEND), depthTest = glIsEnabled(GL_DEPTH_TEST);
    private final boolean depthClamp = glIsEnabled(GL_DEPTH_CLAMP);
    private final boolean cull = glIsEnabled(GL_CULL_FACE), stencil = glIsEnabled(GL_STENCIL_TEST);
    private final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
    private final int depthFunc = glGetInteger(GL_DEPTH_FUNC);
    private final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB);
    private final int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
    private final int equationRgb = glGetInteger(GL_BLEND_EQUATION_RGB), equationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
    private final BlendMode blendMode = ShaderBlendModeAccess.cnpcplus$lastApplied();
    private final Matrix4f textureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
    private final Map<Integer, Integer> textures = new LinkedHashMap<>();
    private final Map<Integer, Integer> samplers = new LinkedHashMap<>();
    private final int[] shaderTextures = new int[12];
    private EffectInstance effect;

    PostRenderState(RenderTarget main) {
        this.main = main;
        glGetIntegerv(GL_VIEWPORT, viewport);
        glGetIntegerv(GL_SCISSOR_BOX, scissorBox);
        glGetIntegerv(GL_COLOR_WRITEMASK, colorMask);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
        for (int i = 0; i < shaderTextures.length; i++) shaderTextures[i] = RenderSystem.getShaderTexture(i);
        // Allocation/destruction also binds textures, before the first EffectInstance.apply.
        activeTexture(activeTexture);
        textures.put(activeTexture, glGetInteger(GL_TEXTURE_BINDING_2D));
        RenderSystem.bindTexture(textures.get(activeTexture)); // Align the cache with the captured binding.
        captureTextures(1);
        activeTexture(GL_TEXTURE0);
        RenderSystem.disableScissor();
        glDisable(GL_SCISSOR_TEST);
        RenderSystem.colorMask(true, true, true, true);
        glColorMask(true, true, true, true);
        RenderSystem.disableBlend();
        glDisable(GL_BLEND);
        RenderSystem.disableDepthTest();
        glDisable(GL_DEPTH_TEST);
        RenderSystem.disableCull();
        glDisable(GL_CULL_FACE);
        glDisable(GL_STENCIL_TEST);
        glEnable(GL_DEPTH_CLAMP);
        RenderSystem.resetTextureMatrix();
        ShaderBlendModeAccess.cnpcplus$lastApplied(null);
    }

    void captureTextures(int count) {
        if (count <= samplers.size()) return;
        int active = GlStateManager._getActiveTexture();
        for (int i = 0; i < count; i++) {
            int unit = GL_TEXTURE0 + i;
            if (!textures.containsKey(unit)) {
                activeTexture(unit);
                int texture = glGetInteger(GL_TEXTURE_BINDING_2D);
                textures.put(unit, texture);
                RenderSystem.bindTexture(texture);
            }
            if (!samplers.containsKey(i)) {
                activeTexture(unit);
                samplers.put(i, glGetInteger(GL_SAMPLER_BINDING));
                glBindSampler(i, 0);
            }
        }
        activeTexture(active);
    }

    void beforePass(EffectInstance effect, int samplerCount) {
        captureTextures(samplerCount);
        this.effect = effect;
        // Lazy target creation/resizing enables depth testing again.
        RenderSystem.disableDepthTest();
        glDisable(GL_DEPTH_TEST);
        // AR binds/unbinds its VAO directly. The cached immediate buffer can
        // otherwise skip bind() and draw this quad with VAO 0 or another mod's VAO.
        BufferUploader.invalidate();
    }

    void beforeDraw() {
        BufferUploader.invalidate();
        RenderSystem.disableDepthTest();
        glDisable(GL_DEPTH_TEST);
        RenderSystem.disableCull();
        glDisable(GL_CULL_FACE);
        glDisable(GL_STENCIL_TEST);
    }

    static void activeTexture(int unit) {
        // The cached getter does not query GL. Keep RenderSystem hooks on both
        // paths; only a cache hit needs a raw call to repair external raw GL.
        boolean cached = GlStateManager._getActiveTexture() == unit;
        RenderSystem.activeTexture(unit);
        if (cached) glActiveTexture(unit);
    }

    @Override public void close() {
        // Also reset EffectInstance's program cache if a sampler/uniform upload failed.
        if (effect != null) effect.clear();
        textures.forEach((unit, texture) -> {
            activeTexture(unit);
            RenderSystem.bindTexture(texture);
            glBindTexture(GL_TEXTURE_2D, texture);
        });
        samplers.forEach((unit, sampler) -> glBindSampler(unit, sampler));
        for (int i = 0; i < shaderTextures.length; i++) RenderSystem.setShaderTexture(i, shaderTextures[i]);
        activeTexture(activeTexture);
        GlStateManager._glUseProgram(program);
        RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        // GlStateManager has no blend-equation cache or separate-equation wrapper.
        glBlendEquationSeparate(equationRgb, equationAlpha);
        if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
        if (blend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
        ShaderBlendModeAccess.cnpcplus$lastApplied(blendMode);
        RenderSystem.depthFunc(depthFunc);
        glDepthFunc(depthFunc);
        RenderSystem.depthMask(depthMask);
        glDepthMask(depthMask);
        if (depthTest) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
        if (depthTest) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
        if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
        if (cull) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
        if (stencil) glEnable(GL_STENCIL_TEST); else glDisable(GL_STENCIL_TEST);
        if (depthClamp) glEnable(GL_DEPTH_CLAMP); else glDisable(GL_DEPTH_CLAMP);
        RenderSystem.colorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
        glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
        if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]); else RenderSystem.disableScissor();
        if (scissor) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
        RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        GlStateManager._clearDepth(clearDepth);
        RenderSystem.setTextureMatrix(textureMatrix);
        BufferUploader.invalidate();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        // Element bindings belong to a VAO; binding one with VAO 0 is invalid in core GL.
        if (vao != 0) GlStateManager._glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementBuffer);
        // At the late GameRenderer hook this also restores Oculus's main-target listener.
        if (draw == main.frameBufferId) main.bindWrite(false);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
}
