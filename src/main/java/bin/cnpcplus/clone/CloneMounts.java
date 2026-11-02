package bin.cnpcplus.clone;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.data.NpcExternalData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.entity.EntityNPCInterface;

/** Native Passengers tree shared by direct/remote copying and both clone-spawning tools. */
public final class CloneMounts {
    private CloneMounts() { }
    public static NBTTagCompound capture(Entity selected) throws IOException {
        Entity root = selected.getLowestRidingEntity();
        List<Entity> originals = new ArrayList<>(); originals.add(root);
        for (int i = 0; i < originals.size(); i++) {
            Entity entity = originals.get(i);
            if (entity instanceof EntityPlayer || entity.isDead) throw new IOException("骑乘组合含玩家或已移除成员，未复制");
            originals.addAll(entity.getPassengers());
        }
        NBTTagCompound tag = new NBTTagCompound();
        if (!root.writeToNBTOptional(tag)) throw new IOException("骑乘根实体无法保存，未复制");
        List<NBTTagCompound> members = members(tag);
        if (members.size() != originals.size()) throw new IOException("骑乘成员无法保存，未复制不完整组合");
        for (int i = 0; i < members.size(); i++) members.get(i).setString("id", EntityList.getKey(originals.get(i)).toString());
        if (!NpcExternalData.makePortable(tag)) throw new IOException("外置数据无法读取，保留原克隆");
        return tag;
    }
    /** Validate saved/network tree once, before any world insertion; unknown IDs cannot be silently skipped. */
    public static List<NBTTagCompound> members(NBTTagCompound root) throws IOException {
        if (root == null) throw new IOException("缺少克隆数据");
        List<NBTTagCompound> members = new ArrayList<>(); members.add(root);
        for (int i = 0; i < members.size(); i++) {
            NBTTagCompound tag = members.get(i);
            if (!tag.hasKey("id", 8) || tag.getString("id").isEmpty()) throw new IOException("缺少骑乘成员实体ID");
            if (!tag.hasKey("Passengers")) continue;
            if (!(tag.getTag("Passengers") instanceof NBTTagList)) throw new IOException("无效的骑乘数组");
            NBTTagList children = (NBTTagList) tag.getTag("Passengers");
            if (!children.isEmpty() && children.getTagType() != 10) throw new IOException("无效的骑乘成员");
            for (int child = 0; child < children.tagCount(); child++) members.add(children.getCompoundTagAt(child));
        }
        return members;
    }
    public static Entity spawn(NBTTagCompound source, double x, double y, double z, World world, Entity vehicle) {
        if (world.isRemote || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        List<Entity> created = new ArrayList<>(); boolean added = false;
        try {
            NBTTagCompound tag = source.copy(); List<NBTTagCompound> members = members(tag);
            if (!NpcExternalData.makePortable(tag)) return null;
            ServerCloneController.Instance.cleanTags(tag);
            Map<NBTTagCompound, Entity> parents = new IdentityHashMap<>();
            for (NBTTagCompound member : members) {
                Entity entity = EntityList.createEntityFromNBT(member, world);
                if (entity == null || entity instanceof EntityPlayer) return null;
                created.add(entity); entity.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
                if (entity instanceof EntityNPCInterface) ((EntityNPCInterface) entity).ais.setStartPos(new BlockPos(entity));
                Entity parent = parents.get(member);
                if (parent != null && !entity.startRiding(parent, true)) return null;
                NBTTagList children = member.getTagList("Passengers", 10);
                for (int i = 0; i < children.tagCount(); i++) parents.put(children.getCompoundTagAt(i), entity);
            }
            Entity root = created.get(0);
            if (vehicle != null && !root.startRiding(vehicle, true)) return null;
            for (Entity entity : created) if (!world.spawnEntity(entity)) return null;
            added = true; return root;
        } catch (IOException | RuntimeException error) {
            CnpcPlus.LOGGER.error("Cannot spawn complete clone mount", error); return null;
        } finally {
            // CNPC setDead schedules revival, not removal. Let the native tick remove aborted members safely.
            if (!added) for (Entity entity : created) { entity.dismountRidingEntity(); entity.isDead = true; }
        }
    }
}
