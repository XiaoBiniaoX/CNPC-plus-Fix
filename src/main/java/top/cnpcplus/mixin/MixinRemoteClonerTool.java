package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import noppes.npcs.CustomItems;
import noppes.npcs.packets.server.*;
import org.spongepowered.asm.mixin.Mixin;

/** Native packet permissions remain intact; only shared list/delete/teleport accept the cloner. */
@Mixin(value = {SPacketRemoteNpcsGet.class, SPacketRemoteNpcDelete.class, SPacketRemoteNpcTp.class}, remap = false)
public abstract class MixinRemoteClonerTool {
    public boolean toolAllowed(ItemStack stack) { return stack.getItem() == CustomItems.wand || stack.getItem() == CustomItems.cloner; }
}
