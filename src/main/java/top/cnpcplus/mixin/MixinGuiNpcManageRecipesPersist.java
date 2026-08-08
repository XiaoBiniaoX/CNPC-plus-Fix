package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import noppes.npcs.client.gui.SubGuiNpcAvailability;
import noppes.npcs.client.gui.global.GuiNpcManageRecipes;
import noppes.npcs.containers.ContainerManageRecipes;
import noppes.npcs.shared.client.gui.components.GuiBasicContainer;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.craftingview.network.PacketHandler;
import top.cnpcplus.persist.PersistedRecipeStore;
import top.cnpcplus.persist.client.PersistRecipeClient;
import top.cnpcplus.persist.network.PacketPersistRecipe;
import top.cnpcplus.persist.network.PacketRequestPersistIds;
import top.cnpcplus.persist.network.PacketUnpersistRecipe;

import java.util.Map;
import java.util.Vector;

@Mixin(GuiNpcManageRecipes.class)
public class MixinGuiNpcManageRecipesPersist {

    @Shadow(remap = false)
    private ContainerManageRecipes container;

    @Inject(method = "m_7856_", at = @At("TAIL"), remap = false)
    private void cnpcplus$addPersistButtons(CallbackInfo ci) {
        GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
        GuiBasicContainer<?> base = (GuiBasicContainer<?>) (Object) this;
        int left = base.guiLeft + 306;
        int top = base.guiTop;
        self.addButton(new GuiButtonNop((IGuiInterface) self, PersistRecipeClient.BTN_PERSIST, left, top + 126, 84, 20, "持久化"));
        self.addButton(new GuiButtonNop((IGuiInterface) self, PersistRecipeClient.BTN_UNPERSIST, left, top + 148, 84, 20, "取消持久化"));
        self.addButton(new GuiButtonNop((IGuiInterface) self, PersistRecipeClient.BTN_CONDITION, left, top + 170, 84, 20, "对话/任务条件"));
        PacketHandler.CHANNEL.sendToServer(new PacketRequestPersistIds());
        PersistRecipeClient.refreshButtons();
    }

    @Inject(method = "setGuiData", at = @At("RETURN"), remap = false)
    private void cnpcplus$refreshOnSelect(CompoundTag compound, CallbackInfo ci) {
        PersistRecipeClient.refreshButtons();
    }

    @Inject(method = "setData", at = @At("RETURN"), remap = false)
    private void cnpcplus$refreshOnList(Vector<String> list, Map<String, Integer> data, CallbackInfo ci) {
        PersistRecipeClient.refreshButtons();
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), remap = false, cancellable = true)
    private void cnpcplus$handlePersistButtons(GuiButtonNop button, CallbackInfo ci) {
        if (button.id != PersistRecipeClient.BTN_PERSIST
                && button.id != PersistRecipeClient.BTN_UNPERSIST
                && button.id != PersistRecipeClient.BTN_CONDITION) return;
        if (this.container == null || this.container.recipe == null || this.container.recipe.getId() == null) {
            ci.cancel();
            return;
        }
        if (button.id == PersistRecipeClient.BTN_CONDITION) {
            GuiNpcManageRecipes self = (GuiNpcManageRecipes) (Object) this;
            ((GuiBasicContainer<?>) self).setSubGui(new SubGuiNpcAvailability(this.container.recipe.availability));
            ci.cancel();
            return;
        }
        ResourceLocation id = this.container.recipe.getId();
        if (button.id == PersistRecipeClient.BTN_PERSIST) {
            if (PersistedRecipeStore.clientContains(id)) {
                ci.cancel();
                return;
            }
            this.container.saveRecipe();
            PacketHandler.CHANNEL.sendToServer(new PacketPersistRecipe(this.container.recipe.writeNBT()));
            PersistedRecipeStore.clientSet(id, true);
        } else {
            if (!PersistedRecipeStore.clientContains(id)) {
                ci.cancel();
                return;
            }
            PacketHandler.CHANNEL.sendToServer(new PacketUnpersistRecipe(id));
            PersistedRecipeStore.clientSet(id, false);
        }
        PersistRecipeClient.refreshButtons();
        ci.cancel();
    }
}
