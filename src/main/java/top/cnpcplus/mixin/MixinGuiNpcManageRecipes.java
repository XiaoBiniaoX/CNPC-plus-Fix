package top.cnpcplus.mixin;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import noppes.npcs.packets.Packets;
import noppes.npcs.packets.server.SPacketRecipeSave;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.accessor.RecipeCarpentryOffsetAccessor;

import java.util.Map;

@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipes {

    @Shadow(remap = false)
    private ContainerManageRecipes container;

    @Shadow(remap = false)
    private Map<String, ResourceLocation> data;

    @Shadow(remap = false)
    private String selected;

    @Shadow(remap = false)
    private GuiCustomScrollNop scroll;



    @Inject(method = "save", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$safeSaveOnSwitch(CallbackInfo ci) {
        // scroll 切换也会走 save：必须按当前 tab 固定 isGlobal，避免 3x3 掉进 4x4 列表
        GuiTextFieldNop.unfocus();
        if (this.selected == null || this.data == null || !this.data.containsKey(this.selected)) {
            ci.cancel();
            return;
        }
        boolean global = this.container.width == 3;
        this.container.saveRecipe();
        this.container.recipe.isGlobal = global;
        ResourceLocation id = this.container.recipe.getId();
        Packets.sendServer(new SPacketRecipeSave(this.container.recipe.writeNBT()));
        if (RecipeController.instance != null && id != null) {
            RecipeController.instance.globalRecipes.remove(id);
            RecipeController.instance.anvilRecipes.remove(id);
            if (global) RecipeController.instance.globalRecipes.put(id, this.container.recipe);
            else RecipeController.instance.anvilRecipes.put(id, this.container.recipe);
        }
        ci.cancel();
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$handleSaveButton(GuiButtonNop button, CallbackInfo ci) {
        // Add: unique ResourceLocation so display name "new" never collides with persisted recipes.
        if (button.id == 3) {
            GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
            self.save();
            this.scroll.clear();
            String name = top.cnpcplus.persist.RecipeIds.uniqueDisplayName("new");
            while (this.data.containsKey(name)) {
                name = name + "_";
            }
            ResourceLocation id = top.cnpcplus.persist.RecipeIds.fresh();
            RecipeCarpentry recipe = new RecipeCarpentry(id, name);
            recipe.isGlobal = this.container.width == 3;
            this.data.put(name, id);
            Packets.sendServer(new SPacketRecipeSave(recipe.writeNBT()));
            ci.cancel();
            return;
        }
        if (button.id == 2) {
            SimpleContainer matrix = ((ContainerManageRecipesAccess)container).cnpcplus$getCraftingMatrix();
            int gridSize = this.container.size;
            ItemStack[] snapshot = new ItemStack[gridSize];
            for (int i = 0; i < gridSize; i++) {
                ItemStack stack = matrix.getItem(i + 1);
                snapshot[i] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
            }

            boolean global = this.container.width == 3;
            this.container.saveRecipe();
            this.container.recipe.isGlobal = global;
            cnpcplus$saveOffset(this.container.recipe, snapshot, this.container.width);

            ResourceLocation savedId = this.container.recipe.getId();
            Packets.sendServer(new SPacketRecipeSave(this.container.recipe.writeNBT()));

            if (RecipeController.instance != null && savedId != null) {
                RecipeController.instance.globalRecipes.remove(savedId);
                RecipeController.instance.anvilRecipes.remove(savedId);
                if (global) RecipeController.instance.globalRecipes.put(savedId, this.container.recipe);
                else RecipeController.instance.anvilRecipes.put(savedId, this.container.recipe);
            }

            top.cnpcplus.craftingview.RecipeGridSnapshot.save(savedId, snapshot);
            cnpcplus$restoreGrid(matrix, snapshot);

            ci.cancel();
        }
    }

    @Inject(method = "m_7856_", at = @At("RETURN"), remap = false)
    private void cnpcplus$renameRecipeMatchLabels(CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        GuiLabel fuzzyLabel = self.getLabel(0);
        if (fuzzyLabel != null) {
            fuzzyLabel.setMessage(Component.translatable("gui.ignoreDamage"));
        }
        GuiLabel nameLabel = self.getLabel(1);
        if (nameLabel != null) {
            nameLabel.setMessage(Component.translatable("gui.ignoreNBT"));
        }
    }

    @ModifyArg(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiLabel;<init>(ILjava/lang/String;II)V", ordinal = 0), index = 1, remap = false)
    private String cnpcplus$replaceIgnoreDamageText(String original) {
        return I18n.get("gui.ignoreDamage");
    }

    @ModifyArg(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/shared/client/gui/components/GuiLabel;<init>(ILjava/lang/String;II)V", ordinal = 1), index = 1, remap = false)
    private String cnpcplus$replaceIgnoreNbtText(String original) {
        return I18n.get("gui.ignoreNBT");
    }

    @Inject(method = "unFocused", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$allowUnicodeRecipeName(GuiTextFieldNop field, CallbackInfo ci) {
        String name = field.getValue();
        if (name == null) name = "";
        name = name.trim();
        if (name.isEmpty()) {
            ci.cancel();
            return;
        }

        String old = this.container.recipe.name;
        if (old != null && old.equals(name)) {
            ci.cancel();
            return;
        }

        String unique = name;
        while (this.data.containsKey(unique) && (old == null || !unique.equals(old))) {
            unique = unique + "_";
        }

        ResourceLocation id = this.data.remove(old);
        if (id == null) id = this.container.recipe.getId();
        this.container.recipe.name = unique;
        this.data.put(unique, id);
        this.selected = unique;
        this.scroll.replace(old, unique);
        field.setValue(unique);
        ci.cancel();
    }

    @Unique
    private static void cnpcplus$saveOffset(RecipeCarpentry recipe, ItemStack[] grid, int width) {
        int firstRow = width;
        int firstColumn = width;
        for (int row = 0; row < width; row++) {
            for (int col = 0; col < width; col++) {
                ItemStack stack = grid[row * width + col];
                if (stack.isEmpty()) continue;
                if (row < firstRow) firstRow = row;
                if (col < firstColumn) firstColumn = col;
            }
        }
        if (firstRow == width || firstColumn == width) return;
        ((RecipeCarpentryOffsetAccessor) recipe).cnpcplus$setOffset(firstColumn, firstRow, true);
    }

    @Unique
    private static void cnpcplus$restoreGrid(SimpleContainer matrix, ItemStack[] snapshot) {
        for (int i = 0; i < snapshot.length; i++) {
            matrix.setItem(i + 1, snapshot[i].copy());
        }
    }
}
