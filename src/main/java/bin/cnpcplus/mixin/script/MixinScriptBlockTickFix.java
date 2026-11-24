package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ExtraScriptHook;
import net.minecraft.tileentity.TileEntity;
import noppes.npcs.api.event.BlockEvent;
import noppes.npcs.blocks.tiles.TileScripted;
import noppes.npcs.blocks.tiles.TileScriptedDoor;
import noppes.npcs.controllers.IScriptBlockHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {TileScripted.class, TileScriptedDoor.class}, remap = false)
public abstract class MixinScriptBlockTickFix extends TileEntity {
    @Inject(method = "func_73660_a", at = @At("TAIL"), require = 1)
    private void cnpcplus$tickfix(CallbackInfo ci) {
        if (!world.isRemote && !isInvalid()) {
            IScriptBlockHandler handler = (IScriptBlockHandler)this;
            handler.runScript(ExtraScriptHook.tickfix(), new BlockEvent.UpdateEvent(handler.getBlock()));
        }
    }
}
