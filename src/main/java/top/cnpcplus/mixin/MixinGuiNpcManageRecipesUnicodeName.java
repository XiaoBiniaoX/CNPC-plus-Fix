package top.cnpcplus.mixin;

import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = GuiNpcManageRecipes.class, remap = false)
public class MixinGuiNpcManageRecipesUnicodeName {

    @Inject(method = "unFocused", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$allowUnicodeRecipeName(GuiTextFieldNop field, CallbackInfo ci) {
        if (field.id != 0) return;

        GuiNpcManageRecipesAccess access = (GuiNpcManageRecipesAccess) this;
        ContainerManageRecipes container = access.cnpcplus$getContainer();
        Map<String, ResourceLocation> data = access.cnpcplus$getData();
        String name = field.getValue() == null ? "" : field.getValue().trim();
        if (name.isEmpty()) {
            field.setValue(container.recipe.name);
            ci.cancel();
            return;
        }

        String old = container.recipe.name;
        if (name.equals(old)) {
            ci.cancel();
            return;
        }

        String unique = name;
        while (data.containsKey(unique) && !unique.equals(old)) {
            unique = unique + "_";
        }

        ResourceLocation id = data.remove(old);
        if (id == null) id = container.recipe.getId();
        container.recipe.name = unique;
        data.put(unique, id);
        access.cnpcplus$setSelected(unique);
        if (access.cnpcplus$getScroll() != null) {
            access.cnpcplus$getScroll().replace(old, unique);
        }
        field.setValue(unique);
        ci.cancel();
    }
}
