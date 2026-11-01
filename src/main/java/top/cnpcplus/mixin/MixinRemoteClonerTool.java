package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import noppes.npcs.CustomItems;
import noppes.npcs.packets.server.SPacketRemoteNpcsGet;
import noppes.npcs.packets.server.SPacketRemoteNpcDelete;
import noppes.npcs.packets.server.SPacketRemoteNpcTp;
import org.spongepowered.asm.mixin.Mixin;

/** Only shared list/read/delete/teleport packets accept the cloner; each native permission remains intact. */
@Mixin(value = {SPacketRemoteNpcsGet.class, SPacketRemoteNpcDelete.class, SPacketRemoteNpcTp.class}, remap = false)
public abstract class MixinRemoteClonerTool {
    public boolean toolAllowed(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == CustomItems.wand || stack.getItem() == CustomItems.cloner);
    }
}
