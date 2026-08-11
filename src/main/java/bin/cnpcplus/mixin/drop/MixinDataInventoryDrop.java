package bin.cnpcplus.mixin.drop;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.world.World;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.util.Random;

/**
 * Drop modes 2/3/4 (drop in place / spread within 1 block / random scatter)
 * ported from 1.20.1 MixinDataInventoryDrop.
 *
 * 1.12.2 vanilla DataInventory already has the lootMode field and NBT
 * persistence ("LootMode"), but its dropStuff() only implements mode 0
 * (scatter) and 1 (auto-pickup). This mixin rewires the default-spawn
 * branch (javap: dropStuff has 4 World.func_72838_d call sites, ordinal 1
 * is the default item branch) and the default xp-orb branch (ordinal 1).
 */
@Mixin(value = DataInventory.class, remap = false)
public class MixinDataInventoryDrop {

    private static final Field CNPCPLUS_NPC = cnpcplus$findNpc();

    private static Field cnpcplus$findNpc() {
        try {
            Field f = DataInventory.class.getDeclaredField("npc");
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    @Unique
    private EntityNPCInterface cnpcplus$npc() {
        if (CNPCPLUS_NPC == null) return null;
        try {
            return (EntityNPCInterface) CNPCPLUS_NPC.get(this);
        } catch (Exception e) {
            return null;
        }
    }

    @Unique
    private boolean cnpcplus$handleItemDrop(World world, Entity entity) {
        EntityNPCInterface npc = cnpcplus$npc();
        int lootMode = ((DataInventory) (Object) this).lootMode;
        if (lootMode >= 2 && npc != null) {
            EntityItem item = (EntityItem) entity;
            Random rand = world.rand;
            if (lootMode == 2) {
                item.setPosition(npc.posX, npc.posY, npc.posZ);
                item.motionX = 0.0;
                item.motionY = 0.0;
                item.motionZ = 0.0;
            } else if (lootMode == 3) {
                item.setPosition(
                    npc.posX + (rand.nextDouble() - 0.5) * 2.0,
                    npc.posY,
                    npc.posZ + (rand.nextDouble() - 0.5) * 2.0
                );
                item.motionX = 0.0;
                item.motionY = 0.0;
                item.motionZ = 0.0;
            } else if (lootMode == 4) {
                double dx = rand.nextDouble() * 2.0 - 1.0;
                double dy = rand.nextDouble() * 2.0 - 1.0;
                double dz = rand.nextDouble() * 2.0 - 1.0;
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 0.01) len = 1.0;
                item.motionX = dx / len * 2.0;
                item.motionY = dy / len * 2.0;
                item.motionZ = dz / len * 2.0;
            }
        }
        return world.spawnEntity(entity);
    }

    @Redirect(
        method = "dropStuff",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_72838_d(Lnet/minecraft/entity/Entity;)Z", ordinal = 1),
        remap = false
    )
    private boolean cnpcplus$onDefaultItemDrop(World world, Entity entity) {
        return cnpcplus$handleItemDrop(world, entity);
    }

    @Redirect(
        method = "dropStuff",
        at = @At(value = "NEW", target = "Lnet/minecraft/entity/item/EntityXPOrb;<init>(Lnet/minecraft/world/World;DDDI)V", ordinal = 1),
        remap = false
    )
    private EntityXPOrb cnpcplus$createExpOrb(World world, double x, double y, double z, int value) {
        EntityNPCInterface npc = cnpcplus$npc();
        int lootMode = ((DataInventory) (Object) this).lootMode;
        if ((lootMode == 3 || lootMode == 4) && npc != null) {
            Random rand = world.rand;
            if (lootMode == 3) {
                x = npc.posX + (rand.nextDouble() - 0.5) * 2.0;
                z = npc.posZ + (rand.nextDouble() - 0.5) * 2.0;
            } else {
                x = npc.posX + (rand.nextDouble() - 0.5) * 4.0;
                z = npc.posZ + (rand.nextDouble() - 0.5) * 4.0;
            }
        }
        return new EntityXPOrb(world, x, y, z, value);
    }
}