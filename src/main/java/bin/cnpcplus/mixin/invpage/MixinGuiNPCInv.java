package bin.cnpcplus.mixin.invpage;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.invpage.DropPageStore;
import bin.cnpcplus.invpage.network.PacketNpcInvPage;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.mainmenu.GuiNPCInv;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcSlider;
import noppes.npcs.containers.ContainerNPCInv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Three-page drop list for GuiNPCInv, matching the 1.20.1 layout:
 * - window enlarged to 224 (npcinv.png is 256 tall, fully covered) so the
 *   prev/next buttons at +195 sit below the hotbar (171..189)
 * - vanilla initGui() unconditionally rewrites chances[0..8] from
 *   dropchance, so session edits are kept in a pending map and applied
 *   unconditionally on every rebuild (prevents first-page loss on flip
 *   and on reopen-after-ESC)
 * ySize is set via reflection: mixins may not extend the target class
 * (InvalidMixinException at runtime) and MC core classes may not be
 * @Mixin targets (pre-loaded by other coremods).
 */
@Mixin(GuiNPCInv.class)
public class MixinGuiNPCInv {

    private static final Field CNPCPLUS_YSIZE = cnpcplus$findYSize();

    private static Field cnpcplus$findYSize() {
        try {
            Field f = net.minecraft.client.gui.inventory.GuiContainer.class.getDeclaredField("field_147000_g");
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    @Shadow(remap = false)
    private HashMap<Integer, Integer> chances;

    @Unique
    private final HashMap<Integer, Integer> cnpcplus$pending = new HashMap<Integer, Integer>();

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void cnpcplus$enlarge(CallbackInfo ci) {
        if (CNPCPLUS_YSIZE == null) return;
        try {
            CNPCPLUS_YSIZE.setInt(this, 224);
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false)
    private void cnpcplus$pageControls(CallbackInfo ci) {
        GuiNPCInv self = (GuiNPCInv) (Object) this;
        GuiContainerNPCInterface base = (GuiContainerNPCInterface) (Object) this;
        int page = DropPageStore.get(base.npc.inventory);
        for (int i = 0; i < 9; i++) {
            GuiNpcSlider slider = base.getSlider(i);
            if (slider == null) continue;
            int slot = page * 9 + i;
            slider.id = slot;
            int chance = 100;
            if (cnpcplus$pending.containsKey(slot)) {
                chance = cnpcplus$pending.get(slot);
            } else if (base.npc.inventory.dropchance.containsKey(slot)) {
                chance = base.npc.inventory.dropchance.get(slot);
            }
            chances.put(slot, chance);
            slider.sliderValue = chance / 100.0f;
        }
        self.addButton(new GuiNpcButton(100, self.field_147003_i + 50, self.field_147009_r + 195, 20, 20, "<"));
        self.addButton(new GuiNpcButton(101, self.field_147003_i + 100, self.field_147009_r + 195, 20, 20, ">"));
        self.addLabel(new GuiNpcLabel(200, (page + 1) + "/3", self.field_147003_i + 75, self.field_147009_r + 200));
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$onPageButton(GuiButton button, CallbackInfo ci) {
        if (button.id != 100 && button.id != 101) return;
        GuiNPCInv self = (GuiNPCInv) (Object) this;
        GuiContainerNPCInterface base = (GuiContainerNPCInterface) (Object) this;
        int page = DropPageStore.get(base.npc.inventory);
        int target = button.id == 100 ? page - 1 : page + 1;
        if (target < 0 || target > 2) {
            ci.cancel();
            return;
        }
        for (int i = 0; i < 9; i++) {
            GuiNpcSlider slider = base.getSlider(i);
            if (slider == null) continue;
            cnpcplus$pending.put(slider.id, (int) (slider.sliderValue * 100));
        }
        DropPageStore.set(base.npc.inventory, target);
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketNpcInvPage(target));
        self.initGui();
        ci.cancel();
    }

    @Inject(method = "mouseReleased", at = @At("TAIL"), remap = false)
    private void cnpcplus$onSliderRelease(GuiNpcSlider slider, CallbackInfo ci) {
        cnpcplus$pending.put(slider.id, (int) (slider.sliderValue * 100));
    }

    @Inject(method = "save", at = @At("HEAD"), remap = false)
    private void cnpcplus$mergeChances(CallbackInfo ci) {
        GuiNPCInv self = (GuiNPCInv) (Object) this;
        GuiContainerNPCInterface base = (GuiContainerNPCInterface) (Object) this;
        for (int i = 0; i < 9; i++) {
            GuiNpcSlider slider = base.getSlider(i);
            if (slider == null) continue;
            cnpcplus$pending.put(slider.id, (int) (slider.sliderValue * 100));
        }
        for (Map.Entry<Integer, Integer> e : cnpcplus$pending.entrySet()) {
            chances.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<Integer, Integer> e : self.npc.inventory.dropchance.entrySet()) {
            if (!chances.containsKey(e.getKey())) {
                chances.put(e.getKey(), e.getValue());
            }
        }
    }
}