package top.cnpcplus.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketCloneSave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SPacketCloneSave.class, remap = false)
public abstract class MixinCloneSaveFeedback {
    @Redirect(method = "handle", at = @At(value = "INVOKE", target = "Lnoppes/npcs/controllers/ServerCloneController;addClone(Lnet/minecraft/nbt/CompoundTag;Ljava/lang/String;I)Ljava/lang/String;"), require = 1)
    private String cnpcplus$reportFailure(ServerCloneController controller, CompoundTag data, String name, int tab) {
        long before = controller.lastLoaded;
        String result = controller.addClone(data, name, tab);
        if (controller.lastLoaded == before) ((PacketServerBasic)(Object)this).player.sendSystemMessage(
                Component.translatable("cnpcplus.clone.saveFailed").withStyle(ChatFormatting.RED));
        return result;
    }
}
