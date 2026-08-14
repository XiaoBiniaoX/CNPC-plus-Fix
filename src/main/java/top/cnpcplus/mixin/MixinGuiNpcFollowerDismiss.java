package top.cnpcplus.mixin;

import net.minecraft.client.resources.language.I18n;
import noppes.npcs.client.gui.player.GuiNpcFollower;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.follower.network.FollowerPacketHandler;
import top.cnpcplus.follower.network.PacketDismissFollower;

@Mixin(value = GuiNpcFollower.class, remap = false)
public class MixinGuiNpcFollowerDismiss {
    @Inject(method = "m_7856_", at = @At("TAIL"))
    private void cnpcplus$addDismissButton(CallbackInfo ci) {
        GuiNpcFollower self = (GuiNpcFollower) (Object) this;
        // 原版「雇佣」按钮（id=5）位于 guiLeft+8, guiTop+30, 50x20（javap 确认）
        // 解雇按钮放其正下方，宽高更小
        self.addButton(new GuiButtonNop((IGuiInterface) self, 1000,
                self.guiLeft + 8, self.guiTop + 52, 40, 14,
                I18n.get("cnpcplus.follower.dismiss")));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$dismiss(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != 1000) return;
        FollowerPacketHandler.CHANNEL.sendToServer(new PacketDismissFollower());
        ci.cancel();
    }
}
