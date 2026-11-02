package top.cnpcplus.clone;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.persist.NpcDataExternalStore;

/** Native Passengers snapshots for copying and both clone-spawning tools. */
public final class CloneMounts {
    private CloneMounts() {}
    public static CompoundTag capture(Entity selected) throws IOException {
        Entity root = selected;
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Entity, Boolean>());
        while (root.getVehicle() != null) {
            if (!seen.add(root)) throw new IOException("骑乘关系循环，未复制");
            root = root.getVehicle();
        }
        seen.clear();
        List<Entity> originals = new ArrayList<>();
        List<Integer> depths = new ArrayList<>();
        originals.add(root);
        depths.add(0);
        for (int i = 0; i < originals.size(); i++) {
            if (!seen.add(originals.get(i)) || depths.get(i) > 31) throw new IOException("骑乘关系循环或超过NBT安全深度，未复制");
            for (Entity passenger : originals.get(i).getPassengers()) {
                originals.add(passenger);
                depths.add(depths.get(i) + 1);
            }
        }
        if (originals.stream().anyMatch(entity -> entity instanceof Player || entity.isRemoved()))
            throw new IOException("骑乘组合含真实玩家或已移除成员，未复制");
        CompoundTag tag = new CompoundTag();
        if (!root.save(tag)) throw new IOException("骑乘根实体无法保存，未复制");
        List<CompoundTag> members = members(tag);
        if (members.size() != originals.size()) throw new IOException("有骑乘成员无法保存，未复制不完整组合");
        for (int i = 0; i < members.size(); i++) {
            members.get(i).putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(originals.get(i).getType()).toString());
            if (!NpcDataExternalStore.makeAllPortable(members.get(i)))
                throw new IOException("骑乘成员外置数据无法读取，保留原克隆");
        }
        checkSize(tag);
        return tag;
    }
    /** Validate the saved/network boundary before copying or native recursive loading. */
    public static List<CompoundTag> members(CompoundTag root) throws IOException {
        if (root == null) throw new IOException("missing clone");
        ClonePreviewData.finite(root, 0);
        checkSize(root);
        List<CompoundTag> result = new ArrayList<>();
        result.add(root);
        for (int i = 0; i < result.size(); i++) {
            CompoundTag tag = result.get(i);
            if (!tag.contains("id", Tag.TAG_STRING) || tag.getString("id").isBlank()) throw new IOException("missing entity id");
            if (tag.contains("Passengers")) {
                if (!(tag.get("Passengers") instanceof ListTag list) || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                    throw new IOException("invalid Passengers");
                for (Tag child : (ListTag) tag.get("Passengers")) result.add((CompoundTag) child);
            }
        }
        return result;
    }
    private static void checkSize(CompoundTag tag) throws IOException {
        if (tag.sizeInBytes() > 2 * 1024 * 1024) throw new IOException("骑乘组合超过原生网络NBT安全大小，未复制");
    }
    /** Failure removes only entities created by this call. */
    public static Entity spawn(CompoundTag source, double x, double y, double z, Level world, Entity vehicle) {
        if (!(world instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || vehicle != null && (vehicle.isRemoved() || vehicle.level() != world)) return null;
        List<Entity> created = new ArrayList<>();
        boolean added = false;
        try {
            List<CompoundTag> members = members(source);
            CompoundTag tag = source.copy();
            ServerCloneController.Instance.cleanTags(tag);
            Entity root = EntityType.loadEntityRecursive(tag, level, entity -> {
                if (entity instanceof Player) throw new IllegalArgumentException("player clone");
                created.add(entity);
                entity.moveTo(x, y, z, entity.getYRot(), entity.getXRot());
                if (entity instanceof EntityNPCInterface npc) npc.ais.setStartPos(npc.blockPosition());
                return entity;
            });
            if (root == null || created.size() != members.size() || root.getSelfAndPassengers().count() != created.size()) return null;
            if (vehicle != null && !root.startRiding(vehicle, true)) return null;
            for (Entity entity : created) if (!level.addFreshEntity(entity)) return null;
            added = true;
            return root;
        } catch (Exception | LinkageError e) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[clone] 完整骑乘生成失败", e);
            return null;
        } finally {
            if (!added) for (Entity entity : created) entity.discard();
        }
    }
}
