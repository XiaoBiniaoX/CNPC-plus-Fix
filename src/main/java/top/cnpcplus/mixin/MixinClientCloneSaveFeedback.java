package top.cnpcplus.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import noppes.npcs.client.controllers.ClientCloneController;
import noppes.npcs.client.gui.GuiNpcMobSpawnerAdd;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GuiNpcMobSpawnerAdd.class, remap = false)
public abstract class MixinClientCloneSaveFeedback {
    @Redirect(method = "accept", at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/controllers/ClientCloneController;addClone(Lnet/minecraft/nbt/CompoundTag;Ljava/lang/String;I)Ljava/lang/String;"), require = 1)
    private String cnpcplus$reportFailure(ClientCloneController controller, CompoundTag data, String name, int tab) {
        long before = controller.lastLoaded;
        String result = controller.addClone(data, name, tab);
        if (controller.lastLoaded == before) Minecraft.getInstance().gui.getChat().addMessage(
                Component.translatable("cnpcplus.clone.saveFailed").withStyle(ChatFormatting.RED));
        return result;
    }
}
