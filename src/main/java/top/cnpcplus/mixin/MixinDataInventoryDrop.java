package top.cnpcplus.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DataInventory.class, remap = false)
public class MixinDataInventoryDrop {

    @Shadow(remap = false)
    public EntityNPCInterface npc;
    @Shadow(remap = false)
    public int lootMode;

    @Unique
    private boolean cnpcplus$handleItemDrop(Level level, Entity entity) {
        if (this.lootMode >= 2) {
            ItemEntity item = (ItemEntity) entity;
            RandomSource rand = this.npc.getRandom();
            if (this.lootMode == 2) {
                item.setPos(this.npc.getX(), this.npc.getY(), this.npc.getZ());
                item.setDeltaMovement(0, 0, 0);
            } else if (this.lootMode == 3) {
                item.setPos(
                    this.npc.getX() + (rand.nextDouble() - 0.5) * 2.0,
                    this.npc.getY(),
                    this.npc.getZ() + (rand.nextDouble() - 0.5) * 2.0
                );
                item.setDeltaMovement(0, 0, 0);
            } else if (this.lootMode == 4) {
                double dx = rand.nextDouble() * 2.0 - 1.0;
                double dy = rand.nextDouble() * 2.0 - 1.0;
                double dz = rand.nextDouble() * 2.0 - 1.0;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 0.01) len = 1.0;
                item.setDeltaMovement(dx / len * 2.0, dy / len * 2.0, dz / len * 2.0);
            }
        }
        return level.addFreshEntity(entity);
    }

    @Redirect(
        method = "dropStuff",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z", ordinal = 1),
        remap = false
    )
    private boolean onSpawnItemMode2(Level level, Entity entity) {
        return cnpcplus$handleItemDrop(level, entity);
    }

    @Redirect(
        method = "dropStuff",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z", ordinal = 2),
        remap = false
    )
    private boolean onSpawnItemMode3(Level level, Entity entity) {
        return cnpcplus$handleItemDrop(level, entity);
    }

    @Redirect(
        method = "dropStuff",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_7967_(Lnet/minecraft/world/entity/Entity;)Z", ordinal = 3),
        remap = false
    )
    private boolean onSpawnItemMode4(Level level, Entity entity) {
        return cnpcplus$handleItemDrop(level, entity);
    }

    @Redirect(
        method = "dropStuff",
        at = @At(value = "NEW", target = "Lnet/minecraft/world/entity/ExperienceOrb;<init>(Lnet/minecraft/world/level/Level;DDDI)V", ordinal = 1),
        remap = false
    )
    private ExperienceOrb createExpOrb(Level level, double x, double y, double z, int value) {
        if (this.lootMode >= 2) {
            RandomSource rand = this.npc.getRandom();
            if (this.lootMode == 3) {
                x = this.npc.getX() + (rand.nextDouble() - 0.5) * 2.0;
                z = this.npc.getZ() + (rand.nextDouble() - 0.5) * 2.0;
            } else if (this.lootMode == 4) {
                x = this.npc.getX() + (rand.nextDouble() - 0.5) * 4.0;
                z = this.npc.getZ() + (rand.nextDouble() - 0.5) * 4.0;
            }
        }
        return new ExperienceOrb(level, x, y, z, value);
    }
}
