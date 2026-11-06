package bin.cnpcplus.mixin.script;

import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.wrapper.BlockWrapper;
import org.spongepowered.asm.mixin.*;
import bin.cnpcplus.shader.*;

@Mixin(value = BlockWrapper.class, remap = false)
public class MixinBlockShaderApi {
    @Shadow @Final protected IWorld world;
    @Shadow @Final protected BlockPos pos;
    @Unique public ShaderManager getShaderManager() { return ShaderRuntime.manager(); }
    @Unique public ShaderController getShaderController(IPlayer player) { return ShaderController.player((EntityPlayerMP)player.getMCEntity()); }
    @Unique public ShaderInstance playShader(IPlayer player, String id, Map<?,?> options) { return getShaderController(player).play(id, options); }
    @Unique public boolean stopShader(IPlayer player, String id) { return getShaderController(player).stop(id); }
    @Unique public ShaderInstance setRenderShader(String id, Map<?,?> options) { return ShaderController.block(world.getMCWorld(), pos).play(id, options); }
    @Unique public void removeRenderShader() { ShaderController.block(world.getMCWorld(), pos).clear(); }
    @Unique public ShaderInstance setRenderShader(IPlayer viewer, String id, Map<?,?> options) { return ShaderController.block(world.getMCWorld(), pos, (EntityPlayerMP)viewer.getMCEntity()).play(id, options); }
    @Unique public void removeRenderShader(IPlayer viewer) { ShaderController.block(world.getMCWorld(), pos, (EntityPlayerMP)viewer.getMCEntity()).clear(); }
}
