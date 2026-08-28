package bin.cnpcplus.mixin.linked;

import bin.cnpcplus.linked.network.PacketLinkedScriptSync;
import bin.cnpcplus.linked.network.PacketLinkedScriptSyncQuery;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import noppes.npcs.client.gui.global.GuiNPCManageLinkedNpc;
import noppes.npcs.packets.Packets;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.listeners.ICustomScrollListener;
import noppes.npcs.shared.client.gui.listeners.IGuiData;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import net.minecraft.client.gui.components.Tooltip;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNPCManageLinkedNpc.class, remap = false)
public class MixinGuiNPCManageLinkedNpcScriptSync implements ICustomScrollListener, IGuiData {

    @Shadow
    private GuiCustomScrollNop scroll;

    @Unique
    private GuiButtonYesNo cnpcplus$scriptSyncBtn;
    @Unique
    private String cnpcplus$selectedName = null;

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$initScriptSync(CallbackInfo ci) {
        GuiNPCManageLinkedNpc self = (GuiNPCManageLinkedNpc)(Object)this;

        this.cnpcplus$scriptSyncBtn = new GuiButtonYesNo(
                (IGuiInterface) self, 100,
                self.guiLeft + 358, self.guiTop + 84,
                58, 20, false
        );
        this.cnpcplus$scriptSyncBtn.setTooltip(Tooltip.create(Component.translatable("cnpcplus.scriptSync")));
        this.cnpcplus$scriptSyncBtn.active = false;
        self.addButton(this.cnpcplus$scriptSyncBtn);
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"))
    private void cnpcplus$buttonEvent(GuiButtonNop button, CallbackInfo ci) {
        if (button.id == 100 && this.cnpcplus$selectedName != null) {
            boolean newState = button.getValue() == 1;
            Packets.sendServer((CustomPacketPayload)new PacketLinkedScriptSync(this.cnpcplus$selectedName, newState));
        }
    }

    @Override
    public void scrollClicked(double i, double j, int k, GuiCustomScrollNop scroll) {
        this.cnpcplus$selectedName = scroll.getSelected();
        if (this.cnpcplus$selectedName != null) {
            Packets.sendServer((CustomPacketPayload)new PacketLinkedScriptSyncQuery(this.cnpcplus$selectedName));
        }
        if (this.cnpcplus$scriptSyncBtn != null) {
            this.cnpcplus$scriptSyncBtn.active = this.cnpcplus$selectedName != null;
        }
    }

    @Override
    public void scrollDoubleClicked(String selection, GuiCustomScrollNop scroll) {
    }

    @Override
    public void setGuiData(CompoundTag compound) {
        if (compound.contains("CNPCPlusScriptSync") && this.cnpcplus$scriptSyncBtn != null) {
            boolean state = compound.getBoolean("CNPCPlusScriptSync");
            this.cnpcplus$scriptSyncBtn.setDisplay(state ? 1 : 0);
        }
    }
}