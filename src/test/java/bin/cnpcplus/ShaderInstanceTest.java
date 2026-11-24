package bin.cnpcplus;

import bin.cnpcplus.shader.*;
import com.google.gson.*;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShaderInstanceTest {
    @Test public void precisePositionSurvivesAnimationPauseAndOldNbt() {
        ShaderManager manager = new ShaderManager();
        manager.register("cnpcplus:precision_test",new JsonParser().parse("{\"type\":\"POST\",\"shaderResource\":\"cnpcplus:test\",\"uniforms\":[{\"name\":\"WorldPosition\",\"type\":\"vec3\",\"default\":[900000.03125,64,0]},{\"name\":\"Opacity\",\"type\":\"float\",\"default\":1}]} ").getAsJsonObject());
        ShaderInstance instance = new ShaderInstance(UUID.randomUUID(),manager.get("cnpcplus:precision_test"),"post");
        assertEquals(900000.03125,instance.worldPosition(0).x,0);
        assertTrue(instance.setUniform("WorldPosition",new double[]{900000.0125,64,0}));
        assertEquals(900000.0125,ShaderInstance.read(instance.save(),manager).worldPosition(0).x,0);
        instance.animateUniform("WorldPosition",new double[]{900000.0125,64,0},new double[]{900000.1125,64,0},4,"linear");
        instance.tick(); instance.pause();
        assertEquals(900000.0375,instance.worldPosition(.9f).x,1e-9);
        ShaderInstance copy = ShaderInstance.read(instance.save(),manager);
        assertEquals(instance.worldPosition(0).x,copy.worldPosition(.9f).x,0);
        copy.resume(); for (int i = 0; i < 3; i++) copy.tick();
        assertEquals(900000.1125,copy.worldPosition(0).x,1e-9);
        assertFalse(copy.isAnimating("WorldPosition"));
        NBTTagCompound old = copy.save(); old.removeTag("PreciseWorldPosition");
        assertEquals(((float[])copy.getUniform("WorldPosition"))[0],ShaderInstance.read(old,manager).worldPosition(0).x,0);
        copy.resetUniforms(); assertEquals(900000.03125,copy.worldPosition(0).x,0);
    }
}
