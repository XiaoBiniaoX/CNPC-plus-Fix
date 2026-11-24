package bin.cnpcplus.shader.client;

import bin.cnpcplus.mixin.shader.ShaderUniformAccess;
import bin.cnpcplus.shader.*;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.FloatBuffer;
import java.util.*;
import net.minecraft.client.shader.ShaderUniform;
import org.junit.Test;
import static org.junit.Assert.*;

/** No GL context: test the real binding method with native Uniform buffers and accessor stand-ins. */
public class ShaderBindingTest {
    static class Uniform extends ShaderUniform implements ShaderUniformAccess {
        final int type, count;
        Uniform(String name, int type, int count) { super(name,type,count,null); this.type = type; this.count = count; }
        public int cnpcplus$type() { return type; }
        public int cnpcplus$count() { return count; }
    }
    @Test public void optionalUniformsKeepGpuLengthAndApiTypeChecks() throws Exception {
        ShaderDefinition definition = new ShaderDefinition("test:optional",new JsonParser().parse("{\"type\":\"POST\",\"shaderResource\":\"test:unused\",\"uniforms\":[{\"name\":\"WorldPosition\",\"type\":\"vec3\",\"default\":[0,0,0]},{\"name\":\"Time\",\"type\":\"float\",\"default\":2}]} ").getAsJsonObject());
        ShaderInstance instance = new ShaderInstance(UUID.randomUUID(),definition,"post"); instance.time = 20;
        assertFalse(instance.setInt("Time",1));
        Field singleton = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe)singleton.get(null);
        net.minecraft.client.shader.ShaderManager program = (net.minecraft.client.shader.ShaderManager)unsafe.allocateInstance(net.minecraft.client.shader.ShaderManager.class);
        Map<String,ShaderUniform> uniforms = new HashMap<>(); Uniform time = new Uniform("Time",4,1); uniforms.put("Time",time);
        Field mapped = program.getClass().getDeclaredField("mappedShaderUniforms"); mapped.setAccessible(true); mapped.set(program,uniforms);
        Method bind = ClientShaders.class.getDeclaredMethod("bindUniforms",ShaderInstance.class,program.getClass(),float.class); bind.setAccessible(true);
        bind.invoke(null,instance,program,0f); // WorldPosition deliberately absent, Time still uploads.
        Field values = ShaderUniform.class.getDeclaredField("uniformFloatBuffer"); values.setAccessible(true);
        assertEquals(3,((FloatBuffer)values.get(time)).get(0),0);
        uniforms.put("WorldPosition",new Uniform("WorldPosition",6,2));
        try { bind.invoke(null,instance,program,0f); fail("Must reject a present GPU uniform with the wrong length"); }
        catch (InvocationTargetException error) { assertTrue(error.getCause() instanceof IllegalArgumentException); }
        uniforms.put("WorldPosition",new Uniform("WorldPosition",5,3));
        try { bind.invoke(null,instance,program,0f); fail("Must reject a present GPU uniform with the wrong type"); }
        catch (InvocationTargetException error) { assertTrue(error.getCause().getMessage().contains("type mismatch")); }
    }
    @Test public void allCachedFailuresSkipGpuWorkWithoutAContext() throws Exception {
        Field failuresField = ClientShaders.class.getDeclaredField("FAILURES"); failuresField.setAccessible(true);
        @SuppressWarnings("unchecked") Map<String,String> failures = (Map<String,String>)failuresField.get(null);
        ShaderDefinition definition = new ShaderDefinition("test:failed",new JsonParser().parse("{\"type\":\"POST\",\"shaderResource\":\"test:bad\"}").getAsJsonObject());
        failures.put(definition.id,"compile failure");
        Method hasPost = ClientShaders.class.getDeclaredMethod("hasPost"); hasPost.setAccessible(true);
        try {
            for (int i = 0; i < 3; i++) {
                ShaderInstance instance = new ShaderInstance(UUID.randomUUID(),definition,"post"); ClientShaders.CONTROLLER.instances.put(instance.token,instance);
                assertEquals(false,hasPost.invoke(null)); assertEquals("ERROR",instance.state); assertEquals("compile failure",instance.error);
            }
            assertEquals(1,failures.size());
        } finally { failures.clear(); ClientShaders.CONTROLLER.instances.clear(); }
    }
}
