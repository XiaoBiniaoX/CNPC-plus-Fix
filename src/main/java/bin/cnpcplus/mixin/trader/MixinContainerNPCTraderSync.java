package bin.cnpcplus.mixin.trader;

import bin.cnpcplus.trader.TraderPager;
import bin.cnpcplus.trader.network.PacketTraderPageSync;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.containers.ContainerNPCTraderSetup;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = {ContainerNPCTrader.class, ContainerNPCTraderSetup.class}, remap = false)
public class MixinContainerNPCTraderSync {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cnpcplus$syncPage(int containerId, Inventory player, int entityId, CallbackInfo ci) {
        if (!(player.player instanceof ServerPlayer sp)) return;
        RoleTrader role;
        if ((Object) this instanceof ContainerNPCTraderSetup setup) {
            role = setup.role;
        } else {
            role = ((ContainerNPCTrader) (Object) this).role;
        }
        PacketDistributor.sendToPlayer(sp, new PacketTraderPageSync(TraderPager.getPage(role)));
    }
}