package bin.cnpcplus.mixin.invpage;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.invpage.DropPageStore;
import bin.cnpcplus.invpage.network.PacketNpcInvPage;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.gui.mainmenu.GuiNPCInv;
import noppes.npcs.client.gui.util.GuiContainerNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcSlider;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Three-page drop list for GuiNPCInv, matching the 1.20.1 layout:
 * - vanilla initGui() unconditionally rewrites chances[0..8] from
 *   dropchance, so session edits are kept in a pending map and applied
 *   unconditionally on every rebuild (prevents first-page loss on flip
 *   and on reopen-after-ESC)
 * 曾把 ySize 扩到 224 让翻页按钮落进窗口：guiTop = (height - ySize) / 2
 * 因此整页比其他页面上移 12px（2026-10-13 用户上报，已删除扩窗段恢复 200）。
 * 翻页按钮 guiTop+195 仍在快捷栏（171..187）下方，npcinv.png 按 256x256
 * 无条件绘制，窗口下沿之外的区域照样有底图，不重叠、不出屏。
 */
@Mixin(GuiNPCInv.class)
public class MixinGuiNPCInv {

    @Shadow(remap = false)
    private HashMap<Integer, Integer> chances;

    @Unique
    private final HashMap<Integer, Integer> cnpcplus$pending = new HashMap<Integer, Integer>();

    private int cnpcplus$lootMode() {
        return Math.max(0, Math.min(4, ((GuiNPCInv) (Object) this).npc.inventory.lootMode));
    }

    @ModifyArg(method = "func_73866_w_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/gui/util/GuiNpcButton;<init>(IIIII[Ljava/lang/String;I)V",
            ordinal = 0, remap = false), index = 5, remap = false, require = 1)
    private String[] cnpcplus$lootOptions(String[] options) {
        return new String[]{"stats.normal", "inv.auto", "cnpcplus.lootMode.feet", "cnpcplus.lootMode.spread", "cnpcplus.lootMode.fly"};
    }

    @ModifyArg(method = "func_73866_w_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/gui/util/GuiNpcButton;<init>(IIIII[Ljava/lang/String;I)V",
            ordinal = 0, remap = false), index = 6, remap = false, require = 1)
    private int cnpcplus$lootButtonValue(int value) {
        return Math.max(0, Math.min(4, value));
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
            slider.displayString = I18n.translateToLocal("inv.dropChance") + ": " + chance + "%";
        }
        self.addButton(new GuiNpcButton(100, self.field_147003_i + 50, self.field_147009_r + 195, 20, 20, "<"));
        self.addButton(new GuiNpcButton(101, self.field_147003_i + 100, self.field_147009_r + 195, 20, 20, ">"));
        self.addLabel(new GuiNpcLabel(200, (page + 1) + "/3", self.field_147003_i + 75, self.field_147009_r + 200));
    }

    /**
     * 进入物品栏界面顿挫修复（用户 2026-10-13 反馈）。
     * Vanilla setGuiData = readEntityFromNBT + func_73866_w_（第二次全量 initGui：
     * 重建 9 滑条/文本框/按钮/标签）。改为原位刷新已建控件后 cancel，
     * 避免整页销毁重建的明显卡顿。
     * Guard: getSlider(0)==null 说明 initGui 尚未跑完，放行原版走全量重建。
     * 刷新时 pending 优先于 dropchance，与 pageControls 一致（未保存会话值不丢）。
     */
    @Inject(method = "setGuiData", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$fastSetGuiData(NBTTagCompound compound, CallbackInfo ci) {
        GuiNPCInv self = (GuiNPCInv) (Object) this;
        GuiContainerNPCInterface base = (GuiContainerNPCInterface) (Object) this;
        if (base.getSlider(0) == null) {
            return;
        }
        self.npc.inventory.readEntityFromNBT(compound);
        int page = DropPageStore.get(base.npc.inventory);
        for (int i = 0; i < 9; i++) {
            GuiNpcSlider slider = base.getSlider(i);
            if (slider == null) continue;
            int slot = page * 9 + i;
            int chance = 100;
            if (cnpcplus$pending.containsKey(slot)) {
                chance = cnpcplus$pending.get(slot);
            } else if (base.npc.inventory.dropchance.containsKey(slot)) {
                chance = base.npc.inventory.dropchance.get(slot);
            }
            chances.put(slot, chance);
            slider.sliderValue = chance / 100.0f;
            slider.displayString = I18n.translateToLocal("inv.dropChance") + ": " + chance + "%";
        }
        GuiNpcTextField tfMin = base.getTextField(0);
        if (tfMin != null) {
            tfMin.setText(self.npc.inventory.getExpMin() + "");
        }
        GuiNpcTextField tfMax = base.getTextField(1);
        if (tfMax != null) {
            tfMax.setText(self.npc.inventory.getExpMax() + "");
        }
        GuiNpcButton lootBtn = base.getButton(10);
        if (lootBtn != null) {
            lootBtn.setDisplay(cnpcplus$lootMode());
            GuiNpcButton picker = base.getButton(31010);
            if (picker != null) picker.displayString = lootBtn.displayString + "…";
        }
        ci.cancel();
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
        GuiNpcTextField.unfocus();
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
