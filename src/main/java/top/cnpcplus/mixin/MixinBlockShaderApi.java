package top.cnpcplus.mixin;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.api.IWorld;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.wrapper.BlockWrapper;
import org.spongepowered.asm.mixin.*;
import top.cnpcplus.shader.*;

@Mixin(value = BlockWrapper.class, remap = false)
public class MixinBlockShaderApi {
    @Shadow @Final protected IWorld level;
    @Shadow @Final protected BlockPos pos;
    @Unique public ShaderManager getShaderManager() { return ShaderRuntime.manager(); }
    @Unique public ShaderController getShaderController(IPlayer player) { return ShaderController.player((ServerPlayer)player.getMCEntity()); }
    @Unique public ShaderInstance playShader(IPlayer player, String id, Map<?,?> options) { return getShaderController(player).play(id, options); }
    @Unique public boolean stopShader(IPlayer player, String id) { return getShaderController(player).stop(id); }
    @Unique public ShaderInstance setRenderShader(String id, Map<?,?> options) { return ShaderController.block(level.getMCLevel(), pos).play(id, options); }
    @Unique public ShaderInstance setRenderShader(String id) { return setRenderShader(id, Map.of()); }
    @Unique public void removeRenderShader() { ShaderController.block(level.getMCLevel(), pos).clear(); }
}
