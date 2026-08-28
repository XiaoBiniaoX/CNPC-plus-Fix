package bin.cnpcplus.mixin.linked;

import net.minecraft.client.gui.GuiButton;
import bin.cnpcplus.common.ILinkedScriptSyncGui;
import noppes.npcs.client.gui.global.GuiNPCManageLinkedNpc;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.ICustomScrollListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import bin.cnpcplus.common.PacketLinkedScriptSync;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = GuiNPCManageLinkedNpc.class, remap = false)
public abstract class MixinGuiNPCManageLinkedNpcScriptSync
        implements ILinkedScriptSyncGui, ICustomScrollListener {

    @Shadow(remap = false)
    private GuiCustomScroll scroll;

    @Unique
    private static Map<String, Boolean> cnpcplus$states = new HashMap<>();

    @Inject(method = "func_73866_w_", at = @At("TAIL"))
    private void cnpcplus$onInitGui(CallbackInfo ci) {
        GuiNPCManageLinkedNpc self = (GuiNPCManageLinkedNpc) (Object) this;
        Map<String, Boolean> pending = PacketLinkedScriptSync.takePendingStates();
        if (pending != null) {
            cnpcplus$states = pending;
        }
        String selected = this.scroll != null ? this.scroll.getSelected() : null;
        boolean isOn = selected != null && cnpcplus$states.containsKey(selected) && cnpcplus$states.get(selected);
        GuiNpcButton toggle = new GuiNpcButton(10, self.guiLeft + 358, self.guiTop + 90, 58, 20,
                new String[]{"cnpcplus.linked.syncScript.off", "cnpcplus.linked.syncScript.on"}, isOn ? 1 : 0);
        toggle.enabled = selected != null && !selected.isEmpty();
        self.addButton(toggle);
    }

    @Inject(method = "buttonEvent", at = @At("TAIL"))
    private void cnpcplus$onButtonEvent(GuiButton button, CallbackInfo ci) {
        GuiNPCManageLinkedNpc self = (GuiNPCManageLinkedNpc) (Object) this;
        if (self.getButton(10) == button) {
            String selected = this.scroll != null ? this.scroll.getSelected() : null;
            if (selected != null && !selected.isEmpty()) {
                CraftingViewNetwork.CHANNEL.sendToServer(PacketLinkedScriptSync.createToggle(selected));
            }
        }
    }

    @Inject(method = "setData", at = @At("HEAD"))
    private void cnpcplus$onSetData(java.util.Vector<String> list, java.util.HashMap<String, Integer> data, CallbackInfo ci) {
        CraftingViewNetwork.CHANNEL.sendToServer(PacketLinkedScriptSync.createRequest());
    }

    @Inject(method = "setSelected", at = @At("TAIL"))
    private void cnpcplus$onSelected(String selected, CallbackInfo ci) {
        cnpcplus$refreshButton();
    }

    @Override
    public void cnpcplus$acceptScriptSyncStates(Map<String, Boolean> states) {
        if (states != null) {
            cnpcplus$states = states;
        }
        cnpcplus$refreshButton();
    }

    @Override
    public void scrollClicked(int x, int y, int button, GuiCustomScroll clickedScroll) {
        if (clickedScroll == this.scroll) {
            cnpcplus$refreshButton();
        }
    }

    @Override
    public void scrollDoubleClicked(String selected, GuiCustomScroll clickedScroll) {
        if (clickedScroll == this.scroll) {
            cnpcplus$refreshButton();
        }
    }

    @Unique
    private void cnpcplus$refreshButton() {
        GuiNPCManageLinkedNpc self = (GuiNPCManageLinkedNpc) (Object) this;
        GuiNpcButton button = self.getButton(10);
        if (button == null) {
            return;
        }
        String selected = this.scroll == null ? null : this.scroll.getSelected();
        button.enabled = selected != null && !selected.isEmpty();
        button.setDisplay(button.enabled && Boolean.TRUE.equals(cnpcplus$states.get(selected)) ? 1 : 0);
    }
}
