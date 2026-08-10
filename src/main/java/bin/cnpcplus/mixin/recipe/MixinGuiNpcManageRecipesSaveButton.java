package bin.cnpcplus.mixin.recipe;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.RecipeNbtKeys;
import bin.cnpcplus.recipe.network.PacketRecipePersist;
import bin.cnpcplus.recipe.storage.RecipePersistent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.client.gui.SubGuiNpcAvailability;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.controllers.data.Availability;
import noppes.npcs.controllers.data.RecipeCarpentry;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketRecipeGet;
import noppes.npcs.packets.server.SPacketRecipeSave;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * GUI lifecycle:
 * - Save button + syncId on save
 * - Rename keeps syncId (fix official unFocused null-id bug)
 * - Add does NOT save empty blank recipes (prevents unnamed + 新建 pair)
 * - Add always works after rename (mutable data map, no force-unfocus side effects)
 */
@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipesSaveButton {

    @Shadow(remap = false)
    private String selected;

    @Shadow(remap = false)
    private GuiCustomScrollNop scroll;

    @Shadow(remap = false)
    private Map data;

    @Shadow(remap = false)
    private ContainerManageRecipes container;

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void cnpcplusAddSaveButton(CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        if (self.getButton(2) == null) {
            self.addButton(new GuiButtonNop((IGuiInterface) self, 2, self.guiLeft + 306, self.guiTop + 104, 84, 20, "gui.save"));
        }
        // 7 = 跨世界持久化, 8 = 取消持久化, 9 = 对话/任务条件
        if (self.getButton(7) == null) {
            self.addButton(new GuiButtonNop((IGuiInterface) self, 7, self.guiLeft + 306, self.guiTop + 126, 84, 20, I18n.get("cnpcplus.recipes.persist")));
        }
        if (self.getButton(8) == null) {
            self.addButton(new GuiButtonNop((IGuiInterface) self, 8, self.guiLeft + 306, self.guiTop + 148, 84, 20, I18n.get("cnpcplus.recipes.unpersist")));
        }
        if (self.getButton(9) == null) {
            self.addButton(new GuiButtonNop((IGuiInterface) self, 9, self.guiLeft + 306, self.guiTop + 170, 84, 20, I18n.get("cnpcplus.recipes.condition")));
        }
        cnpcplusRefresh(self);
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusButtons(GuiButtonNop button, CallbackInfo ci) {
        if (button == null) return;
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;

        // Save
        if (button.id == 2) {
            try {
                cnpcplusSaveCurrent(self, true);
            } catch (Throwable t) {
                CnpcPlus.LOGGER.error("[GUI] save button failed", t);
            }
            ci.cancel();
            return;
        }

        // Persist / Unpersist — requires existing server identity (先点保存)
        if (button.id == 7 || button.id == 8) {
            try {
                int syncId = cnpcplusCurrentSyncId();
                if (syncId > 0) {
                    boolean persist = button.id == 7;
                    PacketDistributor.sendToServer(new PacketRecipePersist(syncId, persist));
                    RecipePersistent.INSTANCE.reloadFromDisk();
                    cnpcplusRefresh(self);
                } else {
                    CnpcPlus.LOGGER.warn("[GUI] persist skipped: save recipe first");
                }
            } catch (Throwable t) {
                CnpcPlus.LOGGER.error("[GUI] persist button failed", t);
            }
            ci.cancel();
            return;
        }

        // Condition — CNPC Availability subgui (对话/任务条件)，随保存一起写入配方
        if (button.id == 9) {
            try {
                if (this.container != null && this.container.recipe != null) {
                    RecipeCarpentry cr = this.container.recipe;
                    self.setSubGui(new SubGuiNpcAvailability(cr.availability));
                }
            } catch (Throwable t) {
                CnpcPlus.LOGGER.error("[GUI] condition button failed", t);
            }
            ci.cancel();
            return;
        }

        // Add
        if (button.id == 3) {
            try {
                // Only save current if it is a real existing/edited recipe — never invent "unnamed"
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

    /** Replace official save() to attach sync id. Skip pure-empty drafts. */
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
        String name = I18n.get("gui.new");
        if (name == null || name.isEmpty()) name = "New";
        // unique display name
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
        this.data.put(name, -1);
        if (this.scroll != null) {
            try {
                this.scroll.setSelected(name);
            } catch (Throwable ignored) {
            }
        }

        CompoundTag nbt = recipe.writeNBT((HolderLookup.Provider) self.player.registryAccess());
        nbt.putString("Name", name);
        // no CnpcPlusSyncId => server creates NEW identity
        Packets.sendServer((CustomPacketPayload) new SPacketRecipeSave(nbt));
        cnpcplusRefresh(self);
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

        // Prefer text field as display name if user typed but did not unfocus
        if (self.getTextField(0) != null) {
            String typed = self.getTextField(0).getValue();
            if (typed != null && !typed.isEmpty()) {
                recipe.name = typed;
            }
        }
        if (recipe.name == null || recipe.name.isEmpty()) {
            recipe.name = this.selected != null && !this.selected.isEmpty() ? this.selected : "unnamed";
        }

        // deep-copy availability so this recipe never shares the instance with another
        if (recipe.availability != null) {
            CompoundTag availTag = new CompoundTag();
            recipe.availability.save(self.player.registryAccess(), availTag);
            Availability fresh = new Availability();
            fresh.load(self.player.registryAccess(), availTag);
            recipe.availability = fresh;
        }

        CompoundTag nbt = recipe.writeNBT((HolderLookup.Provider) self.player.registryAccess());
        nbt.putString("Name", recipe.name);
        int syncId = cnpcplusCurrentSyncId();
        // also try by current recipe name / selected
        if (syncId <= 0 && this.data != null) {
            Object byName = this.data.get(recipe.name);
            if (byName instanceof Integer && (Integer) byName > 0) {
                syncId = (Integer) byName;
            }
        }
        if (syncId > 0) {
            nbt.putInt(RecipeNbtKeys.SYNC_ID, syncId);
        }
        Packets.sendServer((CustomPacketPayload) new SPacketRecipeSave(nbt));
        // keep client map in sync for rename
        cnpcplusEnsureMutableData();
        if (syncId > 0) {
            this.data.put(recipe.name, syncId);
        }
        this.selected = recipe.name;
    }

    private boolean cnpcplusShouldSaveBeforeAdd() {
        // existing server identity
        if (cnpcplusCurrentSyncId() > 0) return true;
        // or user actually put items / result
        return cnpcplusHasMeaningfulRecipe();
    }

    private boolean cnpcplusHasMeaningfulRecipe() {
        if (this.container == null) return false;
        try {
            // result slot 0
            ItemStack result = this.container.getSlot(0) != null ? this.container.getSlot(0).getItem() : ItemStack.EMPTY;
            if (result != null && !result.isEmpty()) return true;
            // any craft slot 1..size
            int size = this.container.size;
            for (int i = 1; i <= size; i++) {
                if (this.container.getSlot(i) != null) {
                    ItemStack s = this.container.getSlot(i).getItem();
                    if (s != null && !s.isEmpty()) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        // named selection that is not a pure draft
        if (this.selected != null && !this.selected.isEmpty()
                && !"unnamed".equals(this.selected)
                && !I18n.get("gui.new").equals(this.selected)) {
            // only if it exists in list with real id
            return cnpcplusCurrentSyncId() > 0;
        }
        return false;
    }

    private int cnpcplusCurrentSyncId() {
        if (this.data == null) return -1;
        if (this.selected != null) {
            Object v = this.data.get(this.selected);
            if (v instanceof Integer && (Integer) v > 0) return (Integer) v;
        }
        if (this.container != null && this.container.recipe != null && this.container.recipe.name != null) {
            Object v = this.data.get(this.container.recipe.name);
            if (v instanceof Integer && (Integer) v > 0) return (Integer) v;
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

    /** Fix official unFocused: capture syncId BEFORE remove(oldName). */
    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplusUnFocused(GuiTextFieldNop field, CallbackInfo ci) {
        if (this.container == null || this.container.recipe == null || field == null) {
            ci.cancel();
            return;
        }
        RecipeCarpentry recipe = this.container.recipe;
        String oldName = recipe.name;
        String newName = field.getValue();
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
        field.setValue(newName);
        if (oldName != null) {
            this.data.remove(oldName);
        }
        recipe.name = newName;
        if (syncId != null && syncId > 0) {
            this.data.put(newName, syncId);
        } else {
            this.data.put(newName, -1);
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
    private void cnpcplusScrollClicked(double i, double j, int k, GuiCustomScrollNop scroll, CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        this.selected = scroll.getSelected();
        if (this.selected != null && this.data != null && this.data.get(this.selected) instanceof Integer) {
            int id = (Integer) this.data.get(this.selected);
            if (id > 0) {
                Packets.sendServer(new SPacketRecipeGet(id));
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
        // always keep a mutable map for rename/add
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
            try {
                if (self.getTextField(0) != null) self.getTextField(0).enabled = true;
                if (self.getButton(5) != null) self.getButton(5).setEnabled(true);
                if (self.getButton(6) != null) self.getButton(6).setEnabled(true);
                if (self.getButton(3) != null) self.getButton(3).setEnabled(true);
                if (self.getButton(2) != null) self.getButton(2).setEnabled(true);
            } catch (Throwable ignored) {
            }
        }
        // always keep Add enabled
        try {
            if (self.getButton(3) != null) self.getButton(3).setEnabled(true);
        } catch (Throwable ignored) {
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
                cnpcplusEnsureMutableData();
                if (compound.contains(RecipeNbtKeys.SYNC_ID)) {
                    this.data.put(name, compound.getInt(RecipeNbtKeys.SYNC_ID));
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
            boolean hasSel = this.selected != null && !this.selected.isEmpty();
            int syncId = cnpcplusCurrentSyncId();
            GuiButtonNop save = self.getButton(2);
            if (save != null) {
                save.setEnabled(hasSel);
            }
            GuiButtonNop add = self.getButton(3);
            if (add != null) {
                add.setEnabled(true);
            }
            boolean canPersist = hasSel && syncId > 0;
            boolean isGlobalTab = this.container != null && this.container.width == 3;
            boolean already = canPersist && RecipePersistent.INSTANCE.isPersistedName(this.selected, isGlobalTab);
            GuiButtonNop persist = self.getButton(7);
            if (persist != null) {
                persist.setEnabled(canPersist && !already);
            }
            GuiButtonNop unpersist = self.getButton(8);
            if (unpersist != null) {
                unpersist.setEnabled(canPersist && already);
            }
            GuiButtonNop condition = self.getButton(9);
            if (condition != null) {
                condition.setEnabled(canPersist);
            }
        } catch (Throwable ignored) {
        }
    }
}