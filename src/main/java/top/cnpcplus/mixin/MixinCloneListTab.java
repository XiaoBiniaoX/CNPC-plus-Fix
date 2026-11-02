package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.packets.client.PacketGuiData;
import noppes.npcs.packets.server.SPacketCloneList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = SPacketCloneList.class, remap = false)
public abstract class MixinCloneListTab {
    @Redirect(method = "sendList", at = @At(value = "NEW", target = "(Lnet/minecraft/nbt/CompoundTag;)Lnoppes/npcs/packets/client/PacketGuiData;"), require = 1)
    private static PacketGuiData cnpcplus$tab(CompoundTag data, ServerPlayer player, int tab) {
        data.putInt("CNPCPlusCloneListTab", tab); return new PacketGuiData(data);
    }
}
