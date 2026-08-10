package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.RecipeNbtKeys;
import bin.cnpcplus.recipe.network.CraftingViewNetworkBridge;
import bin.cnpcplus.recipe.network.PersistClientState;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.Client;
import noppes.npcs.client.gui.SubGuiNpcAvailability;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.controllers.data.RecipeCarpentry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Vector;

@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipesSaveButton {

    @Shadow(remap = false)
    private String selected;

    @Shadow(remap = false)
    private GuiCustomScroll scroll;

    @Shadow(remap = false)
    private HashMap data;

    @Shadow(remap = false)
    private ContainerManageRecipes container;

    @Inject(method = "func_73866_w_", at = @At("RETURN"), remap = false)
    private void cnpcplusAddSaveButton(CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        if (self.getButton(2) == null) {
            self.addButton(new GuiNpcButton(2, self.field_147003_i + 306, self.field_147009_r + 104, 84, 20, "gui.save"));
        }
        // id 7 = persist, id 8 = unpersist (mutually exclusive enable)
        if (self.getButton(7) == null) {
            self.addButton(new GuiNpcButton(7, self.field_147003_i + 306, self.field_147009_r + 126, 84, 20, "cnpcplus.recipe.persist"));
        }
        if (self.getButton(8) == null) {
            self.addButton(new GuiNpcButton(8, self.field_147003_i + 306, self.field_147009_r + 148, 84, 20, "cnpcplus.recipe.unpersist"));
        }
        // id 9 = availability (dialog/quest conditions), opens CNPC native editor
        if (self.getButton(9) == null) {
            self.addButton(new GuiNpcButton(9, self.field_147003_i + 306, self.field_147009_r + 170, 84, 20, "cnpcplus.recipe.availability"));
        }
        cnpcplusRefresh(self);
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusButtons(GuiButton guibutton, CallbackInfo ci) {
        if (guibutton == null) return;
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        int id = guibutton.id;

        if (id == 2) {
            try {
                cnpcplusSaveCurrent(self, true);
            } catch (Throwable t) {
                CnpcPlus.LOGGER.error("[GUI] save button failed", t);
            }
            ci.cancel();
            return;
        }

        if (id == 7) {
            try {
                int syncId = cnpcplusCurrentSyncId();
                if (syncId > 0 && !PersistClientState.isPersisted(syncId)) {
                    PersistClientState.set(syncId, true);
                    CraftingViewNetworkBridge.sendPersist(syncId, true);
                    cnpcplusRefresh(self);
                }
            } catch (Throwable t) {
                CnpcPlus.LOGGER.error("[GUI] persist button failed", t);
            }
            ci.cancel();
            return;
        }

        if (id == 8) {
            try {
                int syncId = cnpcplusCurrentSyncId();
                if (syncId > 0 && PersistClientState.isPersisted(syncId)) {
                    PersistClientState.set(syncId, false);
                    CraftingViewNetworkBridge.sendPersist(syncId, false);
                    cnpcplusRefresh(self);
                }
            } catch (Throwable t) {
                CnpcPlus.LOGGER.error("[GUI] unpersist button failed", t);
            }
            ci.cancel();
            return;
        }

        if (id == 9) {
            try {
                if (this.container != null && this.container.recipe != null) {
                    self.setSubGui(new SubGuiNpcAvailability(this.container.recipe.availability));
                }
            } catch (Throwable t) {
                CnpcPlus.LOGGER.error("[GUI] availability button failed", t);
            }
            ci.cancel();
            return;
        }

        if (id == 3) {
            try {
                if (cnpcplusShouldSaveBeforeAdd()) {
                    cnpcplusSaveCurrent(self, false);
                }
                cnpcplusCreateNewRecipe(self);
            } catch (Throwable t) {
                CnpcPlus.LOGGER.error("[GUI] add button failed", t);
            }
            ci.cancel();
        }
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusSave(CallbackInfo ci) {
        try {
            if (cnpcplusHasMeaningfulRecipe() || cnpcplusCurrentSyncId() > 0) {
                cnpcplusSaveCurrent((GuiNpcManageRecipes) (Object) this, false);
            }
        } catch (Throwable t) {
            CnpcPlus.LOGGER.error("[GUI] save() failed", t);
        }
        ci.cancel();
    }

    private void cnpcplusCreateNewRecipe(GuiNpcManageRecipes self) {
        cnpcplusEnsureMutableData();
        String name = I18n.translateToLocal("gui.new");
        if (name == null || name.isEmpty() || name.equals("gui.new")) name = "New";
        String base = name;
        int n = 0;
        while (this.data.containsKey(name)) {
            n++;
            name = base + "_" + n;
            if (n > 1000) {
                name = base + "_" + System.currentTimeMillis();
                break;
            }
        }

        RecipeCarpentry recipe = new RecipeCarpentry(name);
        recipe.isGlobal = this.container != null && this.container.width == 3;
        this.selected = name;
        this.data.put(name, Integer.valueOf(-1));
        if (this.scroll != null) {
            try {
                this.scroll.setSelected(name);
            } catch (Throwable ignored) {
            }
        }

        NBTTagCompound nbt = recipe.writeNBT();
        nbt.setString("Name", name);
        Client.sendData(EnumPacketServer.RecipeSave, new Object[]{nbt});
        cnpcplusRefresh(self);
        CnpcPlus.LOGGER.info("[GUI] add new recipe name={} global={}", name, Boolean.valueOf(recipe.isGlobal));
    }

    private void cnpcplusSaveCurrent(GuiNpcManageRecipes self, boolean forceUnfocus) {
        if (this.container == null) return;
        if (forceUnfocus && self.getTextField(0) != null) {
            try {
                self.getTextField(0).unFocused();
            } catch (Throwable ignored) {
            }
        }
        this.container.saveRecipe();
        RecipeCarpentry recipe = this.container.recipe;
        if (recipe == null) return;

        if (self.getTextField(0) != null) {
            String typed = self.getTextField(0).getText();
            if (typed != null && typed.length() > 0) {
                recipe.name = typed;
            }
        }
        if (recipe.name == null || recipe.name.isEmpty()) {
            recipe.name = this.selected != null && !this.selected.isEmpty() ? this.selected : "unnamed";
        }

        NBTTagCompound nbt = recipe.writeNBT();
        nbt.setString("Name", recipe.name);
        int syncId = cnpcplusCurrentSyncId();
        if (syncId <= 0 && this.data != null) {
            Object byName = this.data.get(recipe.name);
            if (byName instanceof Integer && ((Integer) byName).intValue() > 0) {
                syncId = ((Integer) byName).intValue();
            }
        }
        if (syncId > 0) {
            nbt.setInteger(RecipeNbtKeys.SYNC_ID, syncId);
            nbt.setInteger("ID", syncId);
            recipe.id = syncId;
        }
        Client.sendData(EnumPacketServer.RecipeSave, new Object[]{nbt});
        cnpcplusEnsureMutableData();
        if (syncId > 0) {
            this.data.put(recipe.name, Integer.valueOf(syncId));
        }
        this.selected = recipe.name;
        CnpcPlus.LOGGER.info("[GUI] save current name={} syncId={} global={}", recipe.name, Integer.valueOf(syncId), Boolean.valueOf(recipe.isGlobal));
    }

    private boolean cnpcplusShouldSaveBeforeAdd() {
        if (cnpcplusCurrentSyncId() > 0) return true;
        return cnpcplusHasMeaningfulRecipe();
    }

    private boolean cnpcplusHasMeaningfulRecipe() {
        if (this.container == null) return false;
        try {
            ItemStack result = this.container.getSlot(0) != null ? this.container.getSlot(0).getStack() : ItemStack.EMPTY;
            if (result != null && !result.isEmpty()) return true;
            int size = this.container.size;
            for (int i = 1; i <= size; i++) {
                if (this.container.getSlot(i) != null) {
                    ItemStack s = this.container.getSlot(i).getStack();
                    if (s != null && !s.isEmpty()) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return cnpcplusCurrentSyncId() > 0;
    }

    private int cnpcplusCurrentSyncId() {
        if (this.data == null) return -1;
        if (this.selected != null) {
            Object v = this.data.get(this.selected);
            if (v instanceof Integer && ((Integer) v).intValue() > 0) return ((Integer) v).intValue();
        }
        if (this.container != null && this.container.recipe != null && this.container.recipe.name != null) {
            Object v = this.data.get(this.container.recipe.name);
            if (v instanceof Integer && ((Integer) v).intValue() > 0) return ((Integer) v).intValue();
        }
        return -1;
    }

    private void cnpcplusEnsureMutableData() {
        if (this.data == null) {
            this.data = new HashMap();
        } else if (!(this.data instanceof HashMap)) {
            this.data = new HashMap(this.data);
        }
    }

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusUnFocused(GuiNpcTextField field, CallbackInfo ci) {
        if (this.container == null || this.container.recipe == null || field == null) {
            ci.cancel();
            return;
        }
        RecipeCarpentry recipe = this.container.recipe;
        String oldName = recipe.name;
        String newName = field.getText();
        if (newName == null) newName = "";
        if (oldName != null && oldName.equals(newName)) {
            ci.cancel();
            return;
        }
        cnpcplusEnsureMutableData();
        Integer syncId = null;
        if (this.selected != null && this.data.get(this.selected) instanceof Integer) {
            syncId = (Integer) this.data.get(this.selected);
        } else if (oldName != null && this.data.get(oldName) instanceof Integer) {
            syncId = (Integer) this.data.get(oldName);
        }
        String tryName = newName;
        int guard = 0;
        while (this.data.containsKey(tryName)) {
            Object existing = this.data.get(tryName);
            if (syncId != null && syncId.equals(existing)) break;
            if (oldName != null && tryName.equals(oldName)) break;
            guard++;
            tryName = newName + "_" + guard;
            if (guard > 1000) break;
        }
        newName = tryName;
        field.setText(newName);
        if (oldName != null) {
            this.data.remove(oldName);
        }
        recipe.name = newName;
        if (syncId != null && syncId.intValue() > 0) {
            this.data.put(newName, syncId);
        } else {
            this.data.put(newName, Integer.valueOf(-1));
        }
        this.selected = newName;
        if (this.scroll != null && oldName != null) {
            try {
                this.scroll.replace(oldName, newName);
            } catch (Throwable t) {
                try {
                    this.scroll.setSelected(newName);
                } catch (Throwable ignored) {
                }
            }
        }
        ci.cancel();
    }

    @Inject(method = "scrollClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusScrollClicked(int i, int j, int k, GuiCustomScroll scroll, CallbackInfo ci) {
        this.selected = scroll.getSelected();
        if (this.selected != null && this.data != null && this.data.get(this.selected) instanceof Integer) {
            int id = ((Integer) this.data.get(this.selected)).intValue();
            if (id > 0) {
                Client.sendData(EnumPacketServer.RecipeGet, new Object[]{Integer.valueOf(id)});
            }
        }
        cnpcplusRefresh((GuiNpcManageRecipes) (Object) this);
        ci.cancel();
    }

    @Inject(method = "setSelected", at = @At("RETURN"), remap = false)
    private void cnpcplusOnSetSelected(String selected, CallbackInfo ci) {
        cnpcplusRefresh((GuiNpcManageRecipes) (Object) this);
    }

    @Inject(method = "setData", at = @At("RETURN"), remap = false)
    private void cnpcplusOnSetData(Vector list, HashMap data, CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        if (data == null) {
            this.data = new HashMap();
        } else {
            this.data = new HashMap(data);
        }
        String name = this.selected;
        if ((name == null || name.isEmpty()) && this.scroll != null) {
            name = this.scroll.getSelected();
        }
        if (name != null && this.data.containsKey(name)) {
            this.selected = name;
            if (this.scroll != null) {
                try {
                    this.scroll.setSelected(name);
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            if (self.getButton(3) != null) self.getButton(3).setEnabled(true);
        } catch (Throwable ignored) {
        }
        cnpcplusRefresh(self);
    }

    @Inject(method = "setGuiData", at = @At("RETURN"), remap = false)
    private void cnpcplusOnSetGuiData(NBTTagCompound compound, CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        if (compound != null && compound.hasKey("Name")) {
            String name = compound.getString("Name");
            if (name != null && !name.isEmpty()) {
                self.setSelected(name);
                cnpcplusEnsureMutableData();
                if (compound.hasKey(RecipeNbtKeys.SYNC_ID)) {
                    this.data.put(name, Integer.valueOf(compound.getInteger(RecipeNbtKeys.SYNC_ID)));
                } else if (compound.hasKey("ID")) {
                    this.data.put(name, Integer.valueOf(compound.getInteger("ID")));
                }
            }
        }
        try {
            if (self.getButton(3) != null) self.getButton(3).setEnabled(true);
        } catch (Throwable ignored) {
        }
        cnpcplusRefresh(self);
    }

    private void cnpcplusRefresh(GuiNpcManageRecipes self) {
        try {
            GuiNpcButton save = self.getButton(2);
            if (save != null) {
                save.setEnabled(this.selected != null && !this.selected.isEmpty());
            }
            GuiNpcButton add = self.getButton(3);
            if (add != null) {
                add.setEnabled(true);
            }
            int syncId = cnpcplusCurrentSyncId();
            boolean has = syncId > 0;
            if (has && !PersistClientState.known(syncId)) {
                CraftingViewNetworkBridge.requestPersistState(syncId);
            }
            boolean p = has && PersistClientState.isPersisted(syncId);

            GuiNpcButton persist = self.getButton(7);
            if (persist != null) {
                persist.displayString = cnpcplusLabel("cnpcplus.recipe.persist", "Persist");
                persist.setEnabled(has && !p);
            }
            GuiNpcButton unpersist = self.getButton(8);
            if (unpersist != null) {
                unpersist.displayString = cnpcplusLabel("cnpcplus.recipe.unpersist", "Unpersist");
                unpersist.setEnabled(has && p);
            }
            GuiNpcButton availability = self.getButton(9);
            if (availability != null) {
                availability.displayString = cnpcplusLabel("cnpcplus.recipe.availability", "Conditions");
                availability.setEnabled(this.selected != null && !this.selected.isEmpty());
            }
        } catch (Throwable ignored) {
        }
    }

    private static String cnpcplusLabel(String key, String fallback) {
        try {
            String s = I18n.translateToLocal(key);
            if (s == null || s.isEmpty() || s.equals(key) || s.startsWith("cnpcplus.")) return fallback;
            return s;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
