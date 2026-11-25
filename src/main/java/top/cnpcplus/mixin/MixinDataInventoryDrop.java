package top.cnpcplus.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve native drops, auto pickup and LootMode NBT; adjust only extended-mode spawns. */
@Mixin(value = DataInventory.class, remap = false)
public abstract class MixinDataInventoryDrop {
    @Shadow private EntityNPCInterface npc;
    @Shadow public int lootMode;

    @Redirect(method = "dropStuff", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", ordinal = 1), require = 1)
    private boolean cnpcplus$dropItem(Level level, Entity entity) {
        ItemEntity item = (ItemEntity) entity;
        RandomSource random = npc.getRandom();
        if (lootMode == 2) {
            item.setPos(npc.getX(), npc.getY(), npc.getZ());
            item.setDeltaMovement(0, 0, 0);
        } else if (lootMode == 3) {
            item.setPos(npc.getX() + (random.nextDouble() - 0.5) * 2,
                    npc.getY(), npc.getZ() + (random.nextDouble() - 0.5) * 2);
            item.setDeltaMovement(0, 0, 0);
        } else if (lootMode == 4) {
            double dx = random.nextDouble() * 2 - 1, dy = random.nextDouble() * 2 - 1, dz = random.nextDouble() * 2 - 1;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 0.01) length = 1;
            item.setDeltaMovement(dx / length * 2, dy / length * 2, dz / length * 2);
        }
        return level.addFreshEntity(entity);
    }

    @Redirect(method = "dropStuff", at = @At(value = "NEW",
            target = "(Lnet/minecraft/world/level/Level;DDDI)Lnet/minecraft/world/entity/ExperienceOrb;", ordinal = 1), require = 1)
    private ExperienceOrb cnpcplus$dropExperience(Level level, double x, double y, double z, int value) {
        if (lootMode == 3 || lootMode == 4) {
            double spread = lootMode == 3 ? 2 : 4;
            RandomSource random = npc.getRandom();
            x = npc.getX() + (random.nextDouble() - 0.5) * spread;
            z = npc.getZ() + (random.nextDouble() - 0.5) * spread;
        }
        return new ExperienceOrb(level, x, y, z, value);
    }
}
