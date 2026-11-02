package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketToolMounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.clone.CloneMounts;

@Mixin(value = SPacketToolMounter.class, remap = false)
public abstract class MixinMounterCloneMount extends PacketServerBasic {
    @Shadow private int type;
    @Shadow private String name;
    @Shadow private int tab;
    @Shadow private CompoundTag compound;

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$mountWholeClone(CallbackInfo ci) {
        if (type != 0 && type != 1) return; // Entity list and player mounting retain their original paths.
        ci.cancel();
        var vehicle = PlayerData.get(player).mounted;
        if (vehicle == null) return;
        CompoundTag tag = type == 0 ? compound : ServerCloneController.Instance.getCloneData(player.createCommandSourceStack(), name, tab);
        if (CloneMounts.spawn(tag, vehicle.getX(), vehicle.getY(), vehicle.getZ(), player.level(), vehicle) == null)
            player.sendSystemMessage(Component.translatable("cnpcplus.clone.mountSpawnFailed"));
    }
}
