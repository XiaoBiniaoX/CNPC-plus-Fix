package top.cnpcplus.mixin;

import noppes.npcs.client.ClientProxy;
import noppes.npcs.client.gui.GuiNpcRemoteEditor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ClientProxy.class, remap = false)
public abstract class MixinClientProxyWandList {
    @Redirect(method = "getGui", at = @At(value = "NEW", target = "()Lnoppes/npcs/client/gui/GuiNpcRemoteEditor;"), require = 1)
    private static GuiNpcRemoteEditor cnpcplus$sharedNpcList() { return new top.cnpcplus.wand.client.GuiWandNpcList(); }
}
