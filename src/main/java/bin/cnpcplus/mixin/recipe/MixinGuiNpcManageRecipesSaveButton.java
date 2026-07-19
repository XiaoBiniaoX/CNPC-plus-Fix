package bin.cnpcplus.mixin.recipe;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketRecipeGet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Vector;

/**
 * Save button only. Selection never auto-saves (prevents 新建_1 spam).
 */
@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipesSaveButton {

    @Shadow(remap = false)
    private String selected;

    @Shadow(remap = false)
    private GuiCustomScrollNop scroll;

    @Shadow(remap = false)
    private Map data;

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void cnpcplusAddSaveButton(CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        if (self.getButton(2) == null) {
            self.addButton(new GuiButtonNop((IGuiInterface) self, 2, self.guiLeft + 306, self.guiTop + 104, 84, 20, "gui.save"));
        }
        cnpcplusRefresh(self);
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusOnSave(GuiButtonNop button, CallbackInfo ci) {
        if (button == null || button.id != 2) return;
        ((GuiNpcManageRecipes) (Object) this).save();
        ci.cancel();
    }

    /** Official scrollClicked always save() first -> ghost recipes. Replace: select only. */
    @Inject(method = "scrollClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusScrollClicked(double i, double j, int k, GuiCustomScrollNop scroll, CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        this.selected = scroll.getSelected();
        if (this.selected != null && this.data != null && this.data.containsKey(this.selected)) {
            Object id = this.data.get(this.selected);
            if (id instanceof Integer) {
                Packets.sendServer(new SPacketRecipeGet((Integer) id));
            }
        }
        cnpcplusRefresh(self);
        ci.cancel();
    }

    @Inject(method = "setSelected", at = @At("RETURN"), remap = false)
    private void cnpcplusOnSetSelected(String selected, CallbackInfo ci) {
        cnpcplusRefresh((GuiNpcManageRecipes) (Object) this);
    }

    @Inject(method = "setData", at = @At("RETURN"), remap = false)
    private void cnpcplusOnSetData(Vector list, Map data, CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        String name = this.selected;
        if ((name == null || name.isEmpty()) && this.scroll != null) {
            name = this.scroll.getSelected();
        }
        if (name != null && data != null && data.containsKey(name)) {
            this.selected = name;
            if (this.scroll != null) {
                this.scroll.setSelected(name);
            }
            try {
                if (self.getTextField(0) != null) self.getTextField(0).enabled = true;
                if (self.getButton(5) != null) self.getButton(5).setEnabled(true);
                if (self.getButton(6) != null) self.getButton(6).setEnabled(true);
            } catch (Throwable ignored) {
            }
        }
        cnpcplusRefresh(self);
    }

    @Inject(method = "setGuiData", at = @At("RETURN"), remap = false)
    private void cnpcplusOnSetGuiData(CompoundTag compound, CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        if (compound != null && compound.contains("Name")) {
            String name = compound.getString("Name");
            if (name != null && !name.isEmpty()) {
                self.setSelected(name);
            }
        }
        cnpcplusRefresh(self);
    }

    private void cnpcplusRefresh(GuiNpcManageRecipes self) {
        try {
            GuiButtonNop save = self.getButton(2);
            if (save != null) {
                save.setEnabled(this.selected != null && !this.selected.isEmpty());
            }
        } catch (Throwable ignored) {
        }
    }
}