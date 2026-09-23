package top.cnpcplus.mixin;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.config.CnpcPlusServerConfig;
import top.cnpcplus.config.ServerConfigAccess;

/** 经用户授权，移植 dochi_real_armor (com.hodu) 的护甲/韧性计算及装备回退。 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinLivingEntityRealArmor {
    @Inject(method = "m_21161_", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$realArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof EntityNPCInterface npc)
                || !ServerConfigAccess.bool(CnpcPlusServerConfig.RealArmorEnabled, false)
                || (npc.role != null && npc.role.getType() == 6
                    && !ServerConfigAccess.bool(CnpcPlusServerConfig.RealArmorOverrideCompanion, false))
                || source.is(DamageTypeTags.BYPASSES_ARMOR)) return;

        double armor = 0.0;
        double toughness = 0.0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            armor += cnpcplus$attribute(stack, slot, Attributes.ARMOR);
            toughness += cnpcplus$attribute(stack, slot, Attributes.ARMOR_TOUGHNESS);
        }
        if (armor <= 0.0 && entity.getArmorValue() <= 0) return;
        armor = Math.max(armor, entity.getArmorValue());
        toughness = Math.max(toughness, entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        if (!entity.level().isClientSide && armor > 0.0 && amount > 0.0f) {
            int durability = Math.max(1, (int) Math.floor(amount / 4.0f));
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (!stack.isEmpty() && stack.isDamageableItem()) {
                    stack.hurtAndBreak(durability, entity, broken -> entity.broadcastBreakEvent(slot));
                }
            }
        }
        cir.setReturnValue(CombatRules.getDamageAfterAbsorb(amount, (float) armor, (float) toughness));
    }

    @Unique
    private static double cnpcplus$attribute(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        double base = 0, multipliedBase = 0, total = 1;
        for (var modifier : stack.getAttributeModifiers(slot).get(attribute)) {
            var operation = modifier.getOperation();
            if (operation == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION)
                base += modifier.getAmount();
            else if (operation == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE)
                multipliedBase += modifier.getAmount();
            else if (operation == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL)
                total *= 1 + modifier.getAmount();
        }
        double value = base * (1 + multipliedBase) * total;
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }
}
