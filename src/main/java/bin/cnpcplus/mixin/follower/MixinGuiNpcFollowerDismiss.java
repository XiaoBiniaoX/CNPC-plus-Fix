package bin.cnpcplus.mixin.follower;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.follower.network.PacketFollowerDismiss;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.player.GuiNpcFollower;
import noppes.npcs.client.gui.util.GuiNpcButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcFollower.class, remap = false)
public class MixinGuiNpcFollowerDismiss {
    @Inject(method = "func_73866_w_", at = @At("RETURN"), remap = false)
    private void cnpcplus$addDismissButton(CallbackInfo ci) {
        GuiNpcFollower self = (GuiNpcFollower) (Object) this;
        self.addButton(new GuiNpcButton(120, self.field_147003_i + 8, self.field_147009_r + 52,
                40, 14, "cnpcplus.follower.dismiss"));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$dismiss(GuiButton button, CallbackInfo ci) {
        if (button.id != 120) return;
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketFollowerDismiss());
        ci.cancel();
    }
}
