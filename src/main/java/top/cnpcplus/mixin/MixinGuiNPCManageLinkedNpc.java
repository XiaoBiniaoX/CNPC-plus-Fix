package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.client.gui.global.GuiNPCManageLinkedNpc;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.linked.network.LinkedPacketHandler;
import top.cnpcplus.linked.network.LinkedSyncClientData;
import top.cnpcplus.linked.network.PacketLinkedRequestSyncStatus;
import top.cnpcplus.linked.network.PacketLinkedToggleSync;

@Mixin(value = GuiNPCManageLinkedNpc.class, remap = false)
public class MixinGuiNPCManageLinkedNpc {

    @Shadow(remap = false)
    private GuiCustomScrollNop scroll;

    @Unique
    private static final int BTN_TOGGLE_SYNC = 100;

    @Unique
    private boolean cnpcplus$requestedSync = false;

    @Unique
    private GuiButtonNop cnpcplus$toggleButton;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addToggleButton(CallbackInfo ci) {
        GuiNPCManageLinkedNpc self = (GuiNPCManageLinkedNpc) (Object) this;
        int value = scroll != null && scroll.hasSelected()
                && LinkedSyncClientData.getSyncScripts(scroll.getSelected()) ? 1 : 0;
        cnpcplus$toggleButton = new GuiButtonNop((IGuiInterface) self, BTN_TOGGLE_SYNC,
                self.guiLeft + 358, self.guiTop + 84, 58, 20,
                new String[]{"cnpcplus.linked.sync.off", "cnpcplus.linked.sync.on"}, value) {
            @Override
            public void m_88315_(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
                if (scroll != null && scroll.hasSelected()) {
                    setDisplay(LinkedSyncClientData.getSyncScripts(scroll.getSelected()) ? 1 : 0);
                }
                super.m_88315_(graphics, mouseX, mouseY, partialTicks);
            }
        };
        self.addButton(cnpcplus$toggleButton);
        if (!cnpcplus$requestedSync) {
            cnpcplus$requestedSync = true;
            LinkedPacketHandler.CHANNEL.sendToServer(new PacketLinkedRequestSyncStatus());
        }
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$handleToggle(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != BTN_TOGGLE_SYNC) return;
        if (scroll == null || !scroll.hasSelected()) {
            ci.cancel();
            return;
        }
        String selected = scroll.getSelected();
        boolean newState = button.getValue() == 1;
        LinkedPacketHandler.CHANNEL.sendToServer(new PacketLinkedToggleSync(selected, newState));
        LinkedSyncClientData.setSyncScripts(selected, newState);
        ci.cancel();
    }
}
