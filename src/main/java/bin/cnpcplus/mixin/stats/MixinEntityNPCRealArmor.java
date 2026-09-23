package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.CombatRules;
import net.minecraft.util.DamageSource;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.20.1 真实盔甲逻辑：四槽属性回退、伙伴开关与服务端耐久。 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCRealArmor {
    @Unique
    private static final EntityEquipmentSlot[] CNPCPLUS_ARMOR_SLOTS = {
            EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
    };

    @Inject(method = "func_70655_b(Lnet/minecraft/util/DamageSource;F)F",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$realArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (!CnpcPlusConfig.isRealArmorEnabled() || source.isUnblockable()
                || (npc.advanced.role == 6 && !CnpcPlusConfig.isRealArmorOverrideCompanion())) return;

        double armor = 0.0D;
        double toughness = 0.0D;
        for (EntityEquipmentSlot slot : CNPCPLUS_ARMOR_SLOTS) {
            ItemStack stack = npc.getItemStackFromSlot(slot);
            if (stack.isEmpty()) continue;
            armor += cnpcplus$attribute(stack, slot, SharedMonsterAttributes.ARMOR);
            toughness += cnpcplus$attribute(stack, slot, SharedMonsterAttributes.ARMOR_TOUGHNESS);
        }
        if (armor <= 0.0D && npc.getTotalArmorValue() <= 0) return;
        armor = Math.max(armor, npc.getTotalArmorValue());
        toughness = Math.max(toughness, npc.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue());
        if (!npc.world.isRemote && armor > 0.0D && amount > 0.0F) {
            int durability = Math.max(1, (int) Math.floor(amount / 4.0F));
            for (EntityEquipmentSlot slot : CNPCPLUS_ARMOR_SLOTS) {
                ItemStack stack = npc.getItemStackFromSlot(slot);
                if (!stack.isEmpty() && stack.isItemStackDamageable()) {
                    stack.damageItem(durability, npc);
                }
            }
        }
        cir.setReturnValue(CombatRules.getDamageAfterAbsorb(amount, (float) armor, (float) toughness));
    }

    @Unique
    private static double cnpcplus$attribute(ItemStack stack, EntityEquipmentSlot slot, IAttribute attribute) {
        double base = 0.0D, multipliedBase = 0.0D, total = 1.0D;
        for (AttributeModifier modifier : stack.getAttributeModifiers(slot).get(attribute.getName())) {
            if (modifier.getOperation() == 0) base += modifier.getAmount();
            else if (modifier.getOperation() == 1) multipliedBase += modifier.getAmount();
            else if (modifier.getOperation() == 2) total *= 1.0D + modifier.getAmount();
        }
        double value = base * (1.0D + multipliedBase) * total;
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }
}
