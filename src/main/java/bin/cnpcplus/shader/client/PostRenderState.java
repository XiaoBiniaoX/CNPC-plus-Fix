package bin.cnpcplus.shader.client;

import bin.cnpcplus.mixin.shader.*;
import java.nio.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.ShaderManager;
import net.minecraft.client.util.JsonBlendingMode;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

/** State touched by legacy post passes, allocation and the client-array uploader. */
final class PostRenderState implements AutoCloseable {
    private final boolean framebufferSupported = OpenGlHelper.isFramebufferEnabled(), split = framebufferSupported && PostPipeline.split();
    private final int draw = framebufferSupported ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) : 0, read = split ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : draw;
    private final int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), unit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final int array = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), renderbuffer = framebufferSupported ? GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING) : 0;
    private final boolean vaoSupported = GLContext.getCapabilities().OpenGL30 || GLContext.getCapabilities().GL_ARB_vertex_array_object;
    private final int vao = vaoSupported ? GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING) : 0;
    private final boolean samplerSupported = GLContext.getCapabilities().OpenGL33 || GLContext.getCapabilities().GL_ARB_sampler_objects;
    private final boolean clampSupported = GLContext.getCapabilities().OpenGL32 || GLContext.getCapabilities().GL_ARB_depth_clamp || GLContext.getCapabilities().GL_NV_depth_clamp;
    private final boolean clamp = clampSupported && GL11.glIsEnabled(GL32.GL_DEPTH_CLAMP);
    private final ShaderManager manager = ShaderProgramAccess.cnpcplus$manager();
    private final JsonBlendingMode blendMode = ShaderBlendAccess.cnpcplus$lastApplied();
    private final int[] bindings = new int[8], samplers = new int[8]; private final boolean[] textures = new boolean[8];
    private final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), fog = GL11.glIsEnabled(GL11.GL_FOG), lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), material = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST), depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
    private final int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA), equation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB), equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final ByteBuffer mask = BufferUtils.createByteBuffer(16);
    private final FloatBuffer clearColor = BufferUtils.createFloatBuffer(16), color = BufferUtils.createFloatBuffer(16);
    private final double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
    private final int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
    PostRenderState() {
        GL11.glGetInteger(GL11.GL_VIEWPORT,viewport); GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK,mask);
        GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE,clearColor); GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);
        for (int i = 0; i < 8; i++) { activeTexture(OpenGlHelper.defaultTexUnit+i); bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); textures[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D); bindTexture(bindings[i]); if (samplerSupported) { samplers[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING); bindSampler(i,0); } }
        if (clampSupported) GL11.glEnable(GL32.GL_DEPTH_CLAMP);
        activeTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.pushMatrix(); GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
        bindVao(0); GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT); clientArrays();
        GlStateManager.disableCull(); GL11.glDisable(GL11.GL_CULL_FACE); GlStateManager.disableDepth(); GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST);
        GlStateManager.colorMask(true,true,true,true); GL11.glColorMask(true,true,true,true);
        ShaderBlendAccess.cnpcplus$invalidate(null); ShaderProgramAccess.cnpcplus$program(-1);
    }
    static void activeTexture(int unit) { GlStateManager.setActiveTexture(unit); OpenGlHelper.setActiveTexture(unit); }
    static void bindTexture(int texture) { GlStateManager.bindTexture(texture); GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture); }
    private static void bindSampler(int unit, int sampler) {
        if (GLContext.getCapabilities().OpenGL33) GL33.glBindSampler(unit,sampler);
        else ARBSamplerObjects.glBindSampler(unit,sampler);
    }
    private static void bindVao(int value) {
        if (GLContext.getCapabilities().OpenGL30) GL30.glBindVertexArray(value);
        else if (GLContext.getCapabilities().GL_ARB_vertex_array_object) ARBVertexArrayObject.glBindVertexArray(value);
    }
    static void clientArrays() { bindVao(0); OpenGlHelper.glBindBuffer(GL15.GL_ARRAY_BUFFER,0); }
    static void beforeDraw() {
        GlStateManager.disableDepth(); GL11.glDisable(GL11.GL_DEPTH_TEST); GlStateManager.disableCull(); GL11.glDisable(GL11.GL_CULL_FACE);
        GlStateManager.disableAlpha(); GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_STENCIL_TEST); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.colorMask(true,true,true,true); GL11.glColorMask(true,true,true,true);
    }
    public void close() {
        bindVao(0); GL11.glPopClientAttrib(); bindVao(vao); OpenGlHelper.glBindBuffer(GL15.GL_ARRAY_BUFFER,array);
        if (framebufferSupported) OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER,renderbuffer);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix(); GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.popMatrix();
        activeTexture(OpenGlHelper.defaultTexUnit); GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.popMatrix(); GlStateManager.matrixMode(matrixMode);
        GlStateManager.tryBlendFuncSeparate(src,dst,srcAlpha,dstAlpha); OpenGlHelper.glBlendFunc(src,dst,srcAlpha,dstAlpha); GL20.glBlendEquationSeparate(equation,equationAlpha);
        if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend(); toggle(GL11.GL_BLEND,blend);
        if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth(); toggle(GL11.GL_DEPTH_TEST,depth); GlStateManager.depthMask(depthMask); GL11.glDepthMask(depthMask);
        if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha(); toggle(GL11.GL_ALPHA_TEST,alpha);
        if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog(); toggle(GL11.GL_FOG,fog);
        if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting(); toggle(GL11.GL_LIGHTING,lighting);
        if (material) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial(); toggle(GL11.GL_COLOR_MATERIAL,material);
        if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull(); toggle(GL11.GL_CULL_FACE,cull); toggle(GL11.GL_STENCIL_TEST,stencil); toggle(GL11.GL_SCISSOR_TEST,scissor);
        GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3)); GL11.glColor4f(color.get(0),color.get(1),color.get(2),color.get(3));
        GlStateManager.colorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0); GL11.glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
        GlStateManager.clearColor(clearColor.get(0),clearColor.get(1),clearColor.get(2),clearColor.get(3)); GL11.glClearColor(clearColor.get(0),clearColor.get(1),clearColor.get(2),clearColor.get(3)); GlStateManager.clearDepth(clearDepth); GL11.glClearDepth(clearDepth);
        for (int i = 0; i < 8; i++) { activeTexture(OpenGlHelper.defaultTexUnit+i); bindTexture(bindings[i]); if (textures[i]) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D(); toggle(GL11.GL_TEXTURE_2D,textures[i]); if (samplerSupported) bindSampler(i,samplers[i]); }
        if (clampSupported) toggle(GL32.GL_DEPTH_CLAMP,clamp);
        activeTexture(unit); OpenGlHelper.glUseProgram(program); ShaderProgramAccess.cnpcplus$program(program); ShaderProgramAccess.cnpcplus$manager(manager); ShaderBlendAccess.cnpcplus$invalidate(blendMode);
        if (framebufferSupported) { OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,draw); if (split) OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read); }
        GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
    }
    private static void toggle(int state, boolean enabled) { if (enabled) GL11.glEnable(state); else GL11.glDisable(state); }
}
