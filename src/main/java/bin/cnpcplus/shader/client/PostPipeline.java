package bin.cnpcplus.shader.client;

import java.nio.ByteBuffer;
import java.util.*;
import bin.cnpcplus.shader.ShaderInstance;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.*;

/** Shared private color surfaces; world depth never aliases the game's depth attachment. */
final class PostPipeline {
    private Framebuffer target, scene;
    private final Map<UUID, Framebuffer[]> histories = new HashMap<>();
    private int depth = -1, depthFbo = -1, depthWidth, depthHeight, depthFormat;
    private boolean depthReady;
    private java.nio.IntBuffer drawBuffers;

    void beginFrame() { depthReady = false; }
    Framebuffer target(int width, int height) {
        if (target == null) { target = new Framebuffer(width,height,true); scene = new Framebuffer(width,height,false); }
        else if (target.framebufferWidth != width || target.framebufferHeight != height) {
            target.createBindFramebuffer(width,height); scene.createBindFramebuffer(width,height);
        }
        return target;
    }
    boolean isTarget(Framebuffer framebuffer) { return framebuffer == target; }
    int sceneColor() { return scene.framebufferTexture; }
    int sceneDepth() { if (!depthReady) throw new IllegalStateException("World depth is not ready"); return depth; }
    int history(UUID token, int index) { return histories.get(token)[index].framebufferTexture; }
    static boolean split() { ContextCapabilities c = GLContext.getCapabilities(); return c.OpenGL30 || c.GL_ARB_framebuffer_object || c.GL_EXT_framebuffer_blit; }
    private static int attachment(int attachment, int property) {
        ContextCapabilities c = GLContext.getCapabilities();
        if (c.OpenGL30) return GL30.glGetFramebufferAttachmentParameteri(OpenGlHelper.GL_FRAMEBUFFER,attachment,property);
        if (c.GL_ARB_framebuffer_object) return ARBFramebufferObject.glGetFramebufferAttachmentParameteri(OpenGlHelper.GL_FRAMEBUFFER,attachment,property);
        return EXTFramebufferObject.glGetFramebufferAttachmentParameteriEXT(OpenGlHelper.GL_FRAMEBUFFER,attachment,property);
    }
    private static void blit(int source, int destination, int x, int y, int width, int height, int outWidth, int outHeight, int mask) {
        OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source);
        OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,destination);
        ContextCapabilities c = GLContext.getCapabilities();
        if (c.OpenGL30) GL30.glBlitFramebuffer(x,y,x+width,y+height,0,0,outWidth,outHeight,mask,GL11.GL_NEAREST);
        else if (c.GL_ARB_framebuffer_object) ARBFramebufferObject.glBlitFramebuffer(x,y,x+width,y+height,0,0,outWidth,outHeight,mask,GL11.GL_NEAREST);
        else EXTFramebufferBlit.glBlitFramebufferEXT(x,y,x+width,y+height,0,0,outWidth,outHeight,mask,GL11.GL_NEAREST);
    }
    /** Small copy boundary: no program/vertex/matrix changes on ordinary frames. */
    void captureDepth(int source, int x, int y, int width, int height) {
        boolean split = split(); int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int read = split ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : draw;
        int unit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        PostRenderState.activeTexture(OpenGlHelper.defaultTexUnit);
        int oldTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int renderbuffer = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,source);
            if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete world framebuffer");
            int type = attachment(OpenGlHelper.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int object = attachment(OpenGlHelper.GL_DEPTH_ATTACHMENT,GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int format;
            if (type == GL11.GL_TEXTURE) {
                PostRenderState.bindTexture(object); format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D,0,GL11.GL_TEXTURE_INTERNAL_FORMAT);
            } else if (type == OpenGlHelper.GL_RENDERBUFFER) {
                OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER,object);
                ContextCapabilities c = GLContext.getCapabilities();
                format = c.OpenGL30 ? GL30.glGetRenderbufferParameteri(OpenGlHelper.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_INTERNAL_FORMAT)
                        : c.GL_ARB_framebuffer_object ? ARBFramebufferObject.glGetRenderbufferParameteri(OpenGlHelper.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_INTERNAL_FORMAT)
                        : EXTFramebufferObject.glGetRenderbufferParameteriEXT(OpenGlHelper.GL_RENDERBUFFER,GL30.GL_RENDERBUFFER_INTERNAL_FORMAT);
            } else throw new IllegalStateException("Missing world depth attachment: " + type);
            boolean packed = format == GL30.GL_DEPTH24_STENCIL8 || format == GL30.GL_DEPTH32F_STENCIL8;
            if (!split && packed) throw new IllegalStateException("Packed world depth requires framebuffer blit support");
            if (depth < 0) depth = GlStateManager.generateTexture();
            PostRenderState.bindTexture(depth);
            if (depthWidth != width || depthHeight != height || depthFormat != format) {
                int dataType = format == GL30.GL_DEPTH32F_STENCIL8 ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV : packed ? GL30.GL_UNSIGNED_INT_24_8 : GL11.GL_FLOAT;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,format,width,height,0,packed ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT,dataType,(ByteBuffer)null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE); GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                depthWidth = width; depthHeight = height; depthFormat = format;
            }
            if (split) {
                if (depthFbo < 0) depthFbo = OpenGlHelper.glGenFramebuffers();
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,depthFbo);
                OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,OpenGlHelper.GL_DEPTH_ATTACHMENT,GL11.GL_TEXTURE_2D,depth,0);
                GL11.glReadBuffer(GL11.GL_NONE); GL11.glDrawBuffer(GL11.GL_NONE);
                if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete world depth snapshot");
                blit(source,depthFbo,x,y,width,height,width,height,GL11.GL_DEPTH_BUFFER_BIT);
            } else GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,x,y,width,height);
            depthReady = true;
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,draw);
            if (split) OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            OpenGlHelper.glBindRenderbuffer(OpenGlHelper.GL_RENDERBUFFER,renderbuffer);
            PostRenderState.bindTexture(oldTexture); PostRenderState.activeTexture(unit);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }
    void captureScene(Framebuffer source) { target(source.framebufferWidth,source.framebufferHeight); copyColor(source,target); }
    void beginEffect(UUID token, boolean history) {
        copyColor(target,scene);
        if (!history) return;
        Framebuffer[] frames = histories.get(token);
        if (frames != null && (frames[0].framebufferWidth != target.framebufferWidth || frames[0].framebufferHeight != target.framebufferHeight)) {
            for (Framebuffer frame : frames) frame.deleteFramebuffer(); histories.remove(token); frames = null;
        }
        if (frames == null) {
            frames = new Framebuffer[3]; histories.put(token,frames);
            for (int i = 0; i < frames.length; i++) { frames[i] = new Framebuffer(target.framebufferWidth,target.framebufferHeight,false); copyColor(scene,frames[i]); }
        }
    }
    void finishEffect(UUID token, boolean advance) {
        Framebuffer[] frames = histories.get(token);
        if (frames != null && advance) { Framebuffer oldest = frames[2]; frames[2] = frames[1]; frames[1] = frames[0]; frames[0] = oldest; copyColor(target,oldest); }
    }
    void rollback() { copyColor(scene,target); }
    static void copyColor(Framebuffer source, Framebuffer destination) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,source.framebufferObject);
        int route = GL11.glGetInteger(GL11.GL_READ_BUFFER); GL11.glReadBuffer(OpenGlHelper.GL_COLOR_ATTACHMENT0);
        try {
            PostRenderState.activeTexture(OpenGlHelper.defaultTexUnit); PostRenderState.bindTexture(destination.framebufferTexture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,source.framebufferWidth,source.framebufferHeight);
        } finally { GL11.glReadBuffer(route); }
    }
    void present(Framebuffer destination) {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        if (split()) {
            OpenGlHelper.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,destination.framebufferObject);
            if (drawBuffers == null) drawBuffers = org.lwjgl.BufferUtils.createIntBuffer(GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS));
            for (int i = 0; i < drawBuffers.capacity(); i++) drawBuffers.put(i,GL11.glGetInteger(GL20.GL_DRAW_BUFFER0+i));
            GL11.glDrawBuffer(OpenGlHelper.GL_COLOR_ATTACHMENT0);
            try { blit(target.framebufferObject,destination.framebufferObject,0,0,target.framebufferWidth,target.framebufferHeight,destination.framebufferWidth,destination.framebufferHeight,GL11.GL_COLOR_BUFFER_BIT); }
            finally { GL20.glDrawBuffers(drawBuffers); }
        } else copyColor(target,destination);
    }
    void retireHistories(Map<UUID,ShaderInstance> instances) {
        for (Iterator<Map.Entry<UUID,Framebuffer[]>> it = histories.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID,Framebuffer[]> entry = it.next(); ShaderInstance instance = instances.get(entry.getKey());
            if (instance != null && instance.isActive()) continue;
            try (PostRenderState state = new PostRenderState()) { for (Framebuffer frame : entry.getValue()) if (frame != null) frame.deleteFramebuffer(); }
            it.remove();
        }
    }
    void close() {
        for (Framebuffer[] frames : histories.values()) for (Framebuffer frame : frames) if (frame != null) frame.deleteFramebuffer(); histories.clear();
        if (target != null) { target.deleteFramebuffer(); target = null; }
        if (scene != null) { scene.deleteFramebuffer(); scene = null; }
        if (depth >= 0) GlStateManager.deleteTexture(depth); depth = -1;
        if (depthFbo >= 0) OpenGlHelper.glDeleteFramebuffers(depthFbo); depthFbo = -1;
        depthWidth = depthHeight = depthFormat = 0; depthReady = false;
    }
}
