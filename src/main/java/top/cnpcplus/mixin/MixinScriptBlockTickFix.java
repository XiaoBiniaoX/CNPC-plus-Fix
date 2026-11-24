package top.cnpcplus.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import noppes.npcs.api.event.BlockEvent;
import noppes.npcs.blocks.tiles.TileScripted;
import noppes.npcs.blocks.tiles.TileScriptedDoor;
import noppes.npcs.controllers.IScriptBlockHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.script.ExtraScriptHook;

@Mixin(value = {TileScripted.class, TileScriptedDoor.class}, remap = false)
public abstract class MixinScriptBlockTickFix {
    @Inject(method = "tick", at = @At("TAIL"))
    private static void cnpcplus$tickFix(Level level, BlockPos pos, BlockState state, @Coerce BlockEntity tile, CallbackInfo ci) {
        if (level.isClientSide || tile.isRemoved()) return;
        var handler = (IScriptBlockHandler) tile;
        if (handler.getEnabled()) handler.runScript(ExtraScriptHook.tickfix(), new BlockEvent.UpdateEvent(handler.getBlock()));
    }
}
