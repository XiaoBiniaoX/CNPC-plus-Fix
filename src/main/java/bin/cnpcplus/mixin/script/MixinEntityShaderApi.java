package bin.cnpcplus.mixin.script;

import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.*;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.wrapper.EntityWrapper;
import org.spongepowered.asm.mixin.*;
import bin.cnpcplus.shader.*;

@Mixin(value = EntityWrapper.class, remap = false)
public class MixinEntityShaderApi {
    @Shadow protected Entity entity;
    @Unique public ShaderManager getShaderManager() { return ShaderRuntime.manager(); }
    @Unique public ShaderController getShaderController() { return entity instanceof EntityPlayerMP ? ShaderController.player((EntityPlayerMP)entity) : entity instanceof EntityPlayer ? ShaderController.local() : ShaderController.entity(entity); }
    @Unique public ShaderInstance playShader(String id) { return getShaderController().play(id); }
    @Unique public ShaderInstance playShader(String id, Map<?,?> options) { return getShaderController().play(id, options); }
    @Unique public ShaderInstance playShader(IPlayer player, String id, Map<?,?> options) { return ShaderController.player((EntityPlayerMP)player.getMCEntity()).play(id, options); }
    @Unique public boolean stopShader(String id) { return getShaderController().stop(id); }
    @Unique public void stopAllShaders() { getShaderController().stopAll(); }
    @Unique public ShaderInstance setRenderShader(String id, Map<?,?> options) { return ShaderController.entity(entity).play(id, options); }
    @Unique public ShaderInstance setRenderShader(String id) { return setRenderShader(id, Collections.emptyMap()); }
    @Unique public void removeRenderShader() { ShaderController.entity(entity).clear(); }
    @Unique public ShaderInstance setRenderShader(IPlayer viewer, String id, Map<?,?> options) { return ShaderController.entity(entity, (EntityPlayerMP)viewer.getMCEntity()).play(id, options); }
    @Unique public void removeRenderShader(IPlayer viewer) { ShaderController.entity(entity, (EntityPlayerMP)viewer.getMCEntity()).clear(); }
}
