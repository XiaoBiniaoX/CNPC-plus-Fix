package bin.cnpcplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * CNPC 准星命中实体时，原版 startUseItem 每次分发实体交互都会重置四 tick 右键冷却。
 * 对弓、食物、药水等持续使用物品，这会让实体交互优先级反复饿死物品使用。
 *
 * <p>仅在客户端命中 CNPC 且主手物品确实存在使用动作时取消这次冷却；服务端的
 * 权限、角色、脚本和实体交互包完全不改，避免影响联机安全。
 */
@Mixin(value = Minecraft.class, remap = false)
public class MinecraftNpcUseItemCooldownMixin {

    @Shadow
    private int rightClickDelay;

    @Shadow
    public net.minecraft.client.player.LocalPlayer player;

    @Shadow
    public net.minecraft.world.phys.HitResult hitResult;

    @Redirect(
            method = "startUseItem",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/Minecraft;rightClickDelay:I",
                    opcode = org.objectweb.asm.Opcodes.PUTFIELD
            ),
            require = 1
    )
    private void cnpcplus$keepUsableItemResponsive(Minecraft minecraft, int delay) {
        ItemStack held = this.player == null ? ItemStack.EMPTY : this.player.getMainHandItem();
        boolean usingItemOnNpc = this.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit
                && hit.getEntity() instanceof EntityNPCInterface
                && !held.isEmpty()
                && held.getUseAnimation() != UseAnim.NONE;

        // 只放行会启动使用状态的物品；普通右键仍保留原版四 tick 节流。
        this.rightClickDelay = usingItemOnNpc ? 0 : delay;
    }
}
