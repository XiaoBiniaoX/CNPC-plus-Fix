package bin.cnpcplus.station;

import bin.cnpcplus.craftingview.RecipeAvailability;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import java.util.Arrays;
import java.util.UUID;

/** Four independently owned cooking slots; conditional recipes pause when their real owner is unavailable. */
public final class CampfireTile extends TileEntity implements ITickable {
    public final NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    private final int[] progress = new int[4], recipes = {-1, -1, -1, -1};
    private final UUID[] owners = new UUID[4];
    public boolean place(EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand); if (held.isEmpty()) return false;
        StationRecipe recipe = StationRecipes.find(2, new ItemStack[]{held}, player);
        boolean custom = false;
        for (StationRecipe candidate : StationRecipes.list(2, world.isRemote)) if (candidate.matchesInputs(new ItemStack[]{held})) { custom = true; break; }
        if (custom && recipe == null || !custom && FurnaceRecipes.instance().getSmeltingResult(held).isEmpty()) return false;
        for (int i = 0; i < 4; i++) if (items.get(i).isEmpty()) {
            if (!world.isRemote) {
                int count = recipe == null ? 1 : recipe.inputs[0].getCount();
                items.set(i, held.copy()); items.get(i).setCount(count);
                if (!player.capabilities.isCreativeMode) held.shrink(count);
                owners[i] = player.getUniqueID(); recipes[i] = recipe == null ? -1 : recipe.id; progress[i] = 0;
                markDirty(); world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
            return true;
        }
        return false;
    }
    @Override public void update() {
        if (world.isRemote) return;
        for (int i = 0; i < 4; i++) {
            ItemStack input = items.get(i); if (input.isEmpty()) continue;
            EntityPlayer owner = owners[i] == null ? null : world.getMinecraftServer().getPlayerList().getPlayerByUUID(owners[i]);
            if (owner != null && owner.world != world) owner = null;
            boolean custom = recipes[i] >= 0 || StationRecipes.hasMatch(2, new ItemStack[]{input});
            StationRecipe recipe = custom ? StationRecipes.find(2, new ItemStack[]{input}, owner) : null;
            if (custom && recipe == null) continue;
            if (recipe != null && recipes[i] != recipe.id) { progress[i] = 0; recipes[i] = recipe.id; }
            if (++progress[i] >= (recipe == null ? 600 : recipe.time)) {
                ItemStack output = recipe == null ? FurnaceRecipes.instance().getSmeltingResult(input).copy() : recipe.outputs[0].copy();
                double x = pos.getX() + 0.5D + (world.rand.nextFloat() * 0.8F - 0.4F);
                double y = pos.getY() + 0.5D + (world.rand.nextFloat() * 0.8F - 0.4F);
                double z = pos.getZ() + 0.5D + (world.rand.nextFloat() * 0.8F - 0.4F);
                net.minecraft.entity.item.EntityItem spawned = new net.minecraft.entity.item.EntityItem(world, x, y, z, output);
                spawned.setDefaultPickupDelay();
                float motion = 0.05F;
                spawned.motionX = world.rand.nextGaussian() * motion;
                spawned.motionY = world.rand.nextGaussian() * motion + 0.2D;
                spawned.motionZ = world.rand.nextGaussian() * motion;
                if (owners[i] != null) {
                    NBTTagCompound production = new NBTTagCompound();
                    production.setUniqueId("Owner", owners[i]);
                    production.setString("Recipe", recipe == null ? "vanilla" : Integer.toString(recipe.id));
                    production.setLong("Position", pos.toLong());
                    spawned.getEntityData().setTag("CNPCPlusCampfireProduction", production);
                }
                if (!world.spawnEntity(spawned)) continue;
                items.set(i, ItemStack.EMPTY); owners[i] = null; recipes[i] = -1; progress[i] = 0;
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
            markDirty();
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag); net.minecraft.inventory.ItemStackHelper.saveAllItems(tag, items);
        tag.setIntArray("CookingProgress", progress); tag.setIntArray("CNPCPlusRecipes", recipes);
        for (int i = 0; i < 4; i++) if (owners[i] != null) tag.setUniqueId("CNPCPlusOwner" + i, owners[i]);
        return tag;
    }
    @Override public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag); items.clear(); net.minecraft.inventory.ItemStackHelper.loadAllItems(tag, items);
        Arrays.fill(progress, 0); Arrays.fill(recipes, -1);
        int[] times = tag.getIntArray("CookingProgress"), ids = tag.getIntArray("CNPCPlusRecipes");
        System.arraycopy(times, 0, progress, 0, Math.min(times.length, 4)); System.arraycopy(ids, 0, recipes, 0, Math.min(ids.length, 4));
        for (int i = 0; i < 4; i++) owners[i] = tag.hasUniqueId("CNPCPlusOwner" + i) ? tag.getUniqueId("CNPCPlusOwner" + i) : null;
    }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) { readFromNBT(packet.getNbtCompound()); }
}
