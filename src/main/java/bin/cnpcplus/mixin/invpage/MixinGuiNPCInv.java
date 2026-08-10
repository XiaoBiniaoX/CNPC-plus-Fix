package bin.cnpcplus.mixin.invpage;

import bin.cnpcplus.invpage.DropPageStore;
import bin.cnpcplus.invpage.network.PacketNpcInvPage;
import bin.cnpcplus.mixin.AbstractContainerScreenAccess;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.client.gui.mainmenu.GuiNPCInv;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@Mixin(value = GuiNPCInv.class, remap = false)
public class MixinGuiNPCInv {

    @Shadow(remap = false)
    public HashMap<Integer, Float> chances;

    @Unique
    private final HashMap<Integer, Float> cnpcplus$pending = new HashMap<>();

    @Unique
    private static final int BTN_PREV = 100;
    @Unique
    private static final int BTN_NEXT = 101;

    private EntityNPCInterface cnpcplus$npc() {
        return ((GuiContainerNPCInterface) (Object) this).npc;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cnpcplus$enlarge(CallbackInfo ci) {
        ((AbstractContainerScreenAccess) this).cnpcplus$setImageHeight(224);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cnpcplus$pageControls(CallbackInfo ci) {
        GuiNPCInv self = (GuiNPCInv) (Object) this;
        EntityNPCInterface npc = cnpcplus$npc();
        int page = DropPageStore.get(npc.inventory);
        for (int i = 0; i < 9; i++) {
            GuiTextFieldNop tf = self.getTextField(2 + i);
            if (tf == null) continue;
            int slot = page * 9 + i;
            tf.id = 2 + slot;
            float chance = 100.0f;
            if (this.cnpcplus$pending.containsKey(slot)) {
                chance = this.cnpcplus$pending.get(slot);
            } else if (npc.inventory.dropchance.containsKey(slot)) {
                chance = npc.inventory.dropchance.get(slot);
            }
            this.chances.put(slot, chance);
            tf.setValue(String.valueOf(chance));
        }
        self.addButton(new GuiButtonNop((IGuiInterface) self, BTN_PREV, self.guiLeft + 50, self.guiTop + 195, 20, 20, "<"));
        self.addButton(new GuiButtonNop((IGuiInterface) self, BTN_NEXT, self.guiLeft + 100, self.guiTop + 195, 20, 20, ">"));
        self.addLabel(new GuiLabel(200, (page + 1) + "/3", self.guiLeft + 75, self.guiTop + 200));
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onPageButton(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != BTN_PREV && button.id != BTN_NEXT) return;
        GuiNPCInv self = (GuiNPCInv) (Object) this;
        EntityNPCInterface npc = cnpcplus$npc();
        int page = DropPageStore.get(npc.inventory);
        int target = button.id == BTN_PREV ? page - 1 : page + 1;
        if (target < 0 || target > 2) {
            ci.cancel();
            return;
        }
        for (int i = 0; i < 9; i++) {
            GuiTextFieldNop tf = self.getTextField(2 + i);
            if (tf == null) continue;
            this.cnpcplus$pending.put(page * 9 + i, tf.getFloat());
        }
        DropPageStore.set(npc.inventory, target);
        PacketDistributor.sendToServer(new PacketNpcInvPage(target));
        self.init();
        ci.cancel();
    }

    @Inject(method = "save", at = @At("HEAD"))
    private void cnpcplus$mergeChances(CallbackInfo ci) {
        this.chances.putAll(this.cnpcplus$pending);
        for (Map.Entry<Integer, Float> e : ((GuiNPCInv) (Object) this).npc.inventory.dropchance.entrySet()) {
            this.chances.putIfAbsent(e.getKey(), e.getValue());
        }
    }
}
