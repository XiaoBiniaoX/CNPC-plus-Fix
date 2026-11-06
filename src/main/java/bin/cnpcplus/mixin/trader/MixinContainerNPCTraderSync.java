package bin.cnpcplus.mixin.trader;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.trader.TraderPager;
import bin.cnpcplus.trader.network.PacketTraderPageSync;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.containers.ContainerNPCTrader;
import noppes.npcs.containers.ContainerNPCTraderSetup;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Role NBT restores page zero on the client. Reset both server inventories
 * before the container sends its slots, then announce the same page.
 */
@Mixin(value = {ContainerNPCTrader.class, ContainerNPCTraderSetup.class}, remap = false)
public class MixinContainerNPCTraderSync {

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void cnpcplus$syncPage(EntityNPCInterface npc, EntityPlayer player, CallbackInfo ci) {
        if (!(player instanceof EntityPlayerMP)) return;
        if (!(npc.roleInterface instanceof RoleTrader)) return;
        RoleTrader role = (RoleTrader) npc.roleInterface;
        TraderPager.switchPage(role, 0);
        CraftingViewNetwork.CHANNEL.sendTo(new PacketTraderPageSync(TraderPager.getPage(role)), (EntityPlayerMP) player);
    }
}
