package top.cnpcplus.mixin;

import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(GuiNpcManageRecipes.class)
public interface GuiNpcManageRecipesAccess {
    @Accessor(value = "container", remap = false)
    ContainerManageRecipes cnpcplus$getContainer();

    @Accessor(value = "data", remap = false)
    Map<String, ResourceLocation> cnpcplus$getData();

    @Accessor(value = "selected", remap = false)
    String cnpcplus$getSelected();

    @Accessor(value = "selected", remap = false)
    void cnpcplus$setSelected(String selected);

    @Accessor(value = "scroll", remap = false)
    GuiCustomScrollNop cnpcplus$getScroll();
}
