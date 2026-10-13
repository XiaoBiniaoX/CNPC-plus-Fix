package top.cnpcplus.mixin;

import noppes.npcs.NBTTags;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAdvanced;
import noppes.npcs.entity.data.DataAI;
import noppes.npcs.entity.data.DataStats;
import noppes.npcs.controllers.data.DataTransform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * S6（Medium）：writeSpawnData 是初始 spawn 精简快照被复用成「全量更新」通道。
 * 补齐调查点名的缺失字段：returnToStart / navigationType / movingPath /
 * faction / transform(激活态) / resistances / melee。
 * 用 @Inject 附加而非 @Overwrite，初始 spawn 包略增但语义一致，客户端
 * readSpawnData 对未知键天然忽略，旧客户端兼容。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCSpawnDataExpand {

    @Shadow(remap = false)
    public DataAI ais;

    @Shadow(remap = false)
    public DataAdvanced advanced;

    @Shadow(remap = false)
    public DataStats stats;

    @Shadow(remap = false)
    public DataTransform transform;

    @Inject(method = "writeSpawnData()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), remap = false)
    private void cnpcplus$addMissingKeys(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag c = cir.getReturnValue();
        if (c == null) return;
        c.putBoolean("ReturnToStart", this.ais.returnToStart);
        c.putInt("NavigationType", this.ais.getNavigationType());
        java.util.List<int[]> path = this.ais.getMovingPath();
        if (path != null && !path.isEmpty()) {
            c.put("MovingPathData", NBTTags.nbtIntegerArraySet(path));
            c.putInt("MovingPathType", this.ais.getMovingPathType());
            c.putBoolean("MovingPathPauses", this.ais.getMovingPathPauses());
        }
        c.putBoolean("FactionAttackOther", this.advanced.attackOtherFactions);
        c.putBoolean("FactionDefend", this.advanced.defendFaction);
        c.put("FactionOptions", this.advanced.factions.save(new CompoundTag()));
        c.putBoolean("TransformIsActive", this.transform.isActive);
        c.put("Resistances", this.stats.resistances.save());
        c.put("MeleeData", this.stats.melee.save(new CompoundTag()));
    }

    @Inject(method = "readSpawnData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), remap = false)
    private void cnpcplus$applyMissingKeys(CompoundTag c, CallbackInfo ci) {
        if (c == null) return;
        if (c.contains("ReturnToStart")) this.ais.returnToStart = c.getBoolean("ReturnToStart");
        if (c.contains("NavigationType")) this.ais.setNavigationType(c.getInt("NavigationType"));
        if (c.contains("MovingPathData")) {
            this.ais.setMovingPath(NBTTags.getIntegerArraySet((ListTag) c.get("MovingPathData")));
            if (c.contains("MovingPathType") && c.contains("MovingPathPauses")) {
                this.ais.setMovingPathType(c.getInt("MovingPathType"), c.getBoolean("MovingPathPauses"));
            }
        }
        if (c.contains("FactionAttackOther")) this.advanced.attackOtherFactions = c.getBoolean("FactionAttackOther");
        if (c.contains("FactionDefend")) this.advanced.defendFaction = c.getBoolean("FactionDefend");
        if (c.contains("FactionOptions")) this.advanced.factions.load(c.getCompound("FactionOptions"));
        if (c.contains("TransformIsActive")) this.transform.isActive = c.getBoolean("TransformIsActive");
        if (c.contains("Resistances")) this.stats.resistances.readToNBT(c.getCompound("Resistances"));
        if (c.contains("MeleeData")) this.stats.melee.load(c.getCompound("MeleeData"));
    }
}
