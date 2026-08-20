package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.smelting.SmeltingBurnTimeHandler;
import net.minecraft.tileentity.TileEntityFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes the furnace being ticked so the burn time event can find it.
 *
 * getItemBurnTime is static, so the FurnaceFuelBurnTimeEvent carries only the
 * item and there is no way to tell which furnace asked. Recording the furnace
 * around update() gives the event handler the context it needs to apply the
 * per-recipe fuel rule.
 *
 * The value is cleared at TAIL and lives in a ThreadLocal, so an exception on
 * one furnace cannot leak a stale reference into another, and the client and
 * server threads never see each other's value.
 */
@Mixin(TileEntityFurnace.class)
public class MixinTileEntityFurnaceBurnContext {
    @Inject(method = "update", at = @At("HEAD"))
    private void cnpcplus$beginFurnaceTick(CallbackInfo ci) {
        SmeltingBurnTimeHandler.beginFurnace((TileEntityFurnace) (Object) this);
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void cnpcplus$endFurnaceTick(CallbackInfo ci) {
        SmeltingBurnTimeHandler.endFurnace();
    }
}
