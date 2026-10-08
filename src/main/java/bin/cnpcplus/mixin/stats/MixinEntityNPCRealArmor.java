package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.core.Holder;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 真实护甲：让 NPC 身上的原版盔甲真正参与减伤，并按「伤害/4」消耗耐久。
 * 移植自 1.20.1 {@code MixinLivingEntityRealArmor}（与 1.12.2 {@code MixinEntityNPCRealArmor} 等价）。
 *
 * <p>必须注入 CNPC 自己覆写的 {@code getDamageAfterArmorAbsorb} —— 它不调 super，
 * 注入 {@code LivingEntity} 会被完全绕过（1.20.1 已踩过此坑）。
 * CNPC 该方法只处理 role==6 伙伴，非伙伴 NPC 的装备护甲此前纯展示：不减伤、不掉耐久。
 *
 * <p>1.21.1 差异：公式 {@code CombatRules.getDamageAfterAbsorb} 由 3 参变 5 参
 * （多了 entity 与 damageSource，用于武器附魔克制），运行时为官方名（javap 实证）。
 * 无 NBT、无数据包；开关走 COMMON 配置，热改热生效。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCRealArmor {

    private static final EquipmentSlot[] CNPCPLUS$ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    @Inject(method = "getDamageAfterArmorAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F",
            at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void cnpcplus$realArmor(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
        // 关开关时完全放行原逻辑（回到「只存不生效」现状），便于对照与回滚。
        if (!CnpcPlusConfig.REAL_ARMOR_ENABLED.get()) return;
        if (source == null || damage <= 0.0f || source.is(DamageTypeTags.BYPASSES_ARMOR)) return;

        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        // role 默认是 RoleInterface.NONE 不会为 null，保留判空以符合本项目空值自检惯例。
        // 伙伴（role 6）已有自带的 (25-armor)/25 简化公式，不覆盖时必须交回原逻辑，否则双重减伤。
        if (npc.role != null && npc.role.getType() == 6
                && !CnpcPlusConfig.REAL_ARMOR_OVERRIDE_COMPANION.get()) return;

        double armor = 0.0;
        double toughness = 0.0;
        for (EquipmentSlot slot : CNPCPLUS$ARMOR_SLOTS) {
            ItemStack stack = npc.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            armor += cnpcplus$slotAttribute(stack, slot, Attributes.ARMOR);
            toughness += cnpcplus$slotAttribute(stack, slot, Attributes.ARMOR_TOUGHNESS);
        }
        // 四件都没有甲时直接放行，保持与原版（无甲不减伤不掉耐久）一致。
        if (armor <= 0.0 && npc.getArmorValue() <= 0) return;
        armor = Math.max(armor, npc.getArmorValue());
        toughness = Math.max(toughness, npc.getAttributeValue(Attributes.ARMOR_TOUGHNESS));

        // 耐久必须自己扣：原版 LivingEntity.hurtArmor 对 NPC 是空实现，
        // 只有 Player/Wolf 覆写，不扣的话就是「无限耐久的护甲减伤」。
        if (!npc.level().isClientSide && armor > 0.0) {
            int durability = Math.max(1, (int) Math.floor(damage / 4.0f));
            for (EquipmentSlot slot : CNPCPLUS$ARMOR_SLOTS) {
                ItemStack stack = npc.getItemBySlot(slot);
                if (!stack.isEmpty() && stack.isDamageableItem()) {
                    // 1.21.1 签名：hurtAndBreak(int, LivingEntity, EquipmentSlot)，断了会自己广播损坏事件。
                    stack.hurtAndBreak(durability, npc, slot);
                }
            }
        }

        LivingEntity entity = npc;
        cir.setReturnValue(CombatRules.getDamageAfterAbsorb(
                entity, damage, source, (float) armor, (float) toughness));
    }

    /**
     * 聚合一件装备上某个属性的修饰器值，语义与 1.20.1 的
     * {@code getAttributeModifiers(slot).get(attr)} + 三级运算一致：
     * {@code base * (1 + multiplyBase) * multiplyTotal}，非有限值与负值归 0。
     *
     * <p>1.21.1 没有按槽位取 Multimap 的旧 API，改用 {@code ItemStack.forEachModifier(slot, ...)}
     * （会连同附魔附加的修饰器一起回调，属性持有者用实例比较即可：注册表属性是单例）。
     */
    @Unique
    private static double cnpcplus$slotAttribute(ItemStack stack, EquipmentSlot slot, Holder<Attribute> attribute) {
        double[] acc = new double[]{0.0, 0.0, 1.0}; // [base, multiplyBase, multiplyTotal]
        stack.forEachModifier(slot, (Holder<Attribute> attr, AttributeModifier modifier) -> {
            if (attr.value() != attribute.value()) return;
            // 1.21.1 的 AttributeModifier 是 record（取值器 operation()/amount()），
            // 且 NeoForge 把枚举常量由 1.20.1 的 ADDITION/MULTIPLY_BASE/MULTIPLY_TOTAL
            // 改名为 ADD_VALUE/ADD_MULTIPLIED_BASE/ADD_MULTIPLIED_TOTAL。
            AttributeModifier.Operation op = modifier.operation();
            if (op == AttributeModifier.Operation.ADD_VALUE) {
                acc[0] += modifier.amount();
            } else if (op == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) {
                acc[1] += modifier.amount();
            } else if (op == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
                acc[2] *= 1.0 + modifier.amount();
            }
        });
        double value = acc[0] * (1.0 + acc[1]) * acc[2];
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }
}
