package top.cnpcplus.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.ServerEventsHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.QuestAreaKillContext;
import top.cnpcplus.quest.QuestDamageLedger;

@Mixin(value = ServerEventsHandler.class, remap = false)
public abstract class MixinServerEventsHandlerQuestDamage {
    @Inject(method = "invoke", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$beginDamageCredit(LivingDeathEvent event, CallbackInfo ci) {
        if (!event.getEntity().level().isClientSide) QuestDamageLedger.beginDeath(event.getEntity());
    }

    @Redirect(method = "invoke", at = @At(value = "INVOKE", target = "Lnoppes/npcs/NoppesUtilServer;GetDamageSourcee(Lnet/minecraft/world/damagesource/DamageSource;)Lnet/minecraft/world/entity/Entity;"), remap = false, require = 1)
    private Entity cnpcplus$useDamageShare(DamageSource source) {
        Entity credited = QuestDamageLedger.currentCreditedPlayer();
        return credited == null ? NoppesUtilServer.GetDamageSourcee(source) : credited;
    }

    @Inject(method = "invoke", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$endDamageCredit(LivingDeathEvent event, CallbackInfo ci) {
        QuestDamageLedger.endDeath();
    }

    @Inject(method = "doQuest", at = @At("HEAD"), remap = false, require = 1)
    private void cnpcplus$areaContextStart(net.minecraft.world.entity.player.Player player, LivingEntity victim, boolean all, CallbackInfo ci) {
        QuestAreaKillContext.push(victim);
    }

    @Inject(method = "doQuest", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$areaContextEnd(net.minecraft.world.entity.player.Player player, LivingEntity victim, boolean all, CallbackInfo ci) {
        QuestAreaKillContext.pop();
    }
}
