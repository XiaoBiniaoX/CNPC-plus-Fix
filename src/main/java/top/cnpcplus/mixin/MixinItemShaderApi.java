package top.cnpcplus.mixin;

import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.api.wrapper.ItemScriptedWrapper;
import org.spongepowered.asm.mixin.*;
import top.cnpcplus.shader.*;

@Mixin(value = ItemScriptedWrapper.class, remap = false)
public class MixinItemShaderApi {
    @Unique public ShaderManager getShaderManager() { return ShaderRuntime.manager(); }
    @Unique public ShaderController getShaderController(IPlayer player) { return ShaderController.player((ServerPlayer)player.getMCEntity()); }
    @Unique public ShaderInstance playShader(IPlayer player, String id, Map<?,?> options) { return getShaderController(player).play(id, options); }
    @Unique public boolean stopShader(IPlayer player, String id) { return getShaderController(player).stop(id); }
    @Unique public ShaderInstance setRenderShader(IPlayer player, String id, Map<?,?> options) {
        return ShaderController.item(((ItemScriptedWrapper)(Object)this).getMCItemStack(), (ServerPlayer)player.getMCEntity()).play(id, options);
    }
    @Unique public void removeRenderShader(IPlayer player) { ShaderController.item(((ItemScriptedWrapper)(Object)this).getMCItemStack(), (ServerPlayer)player.getMCEntity()).clear(); }
}
