package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.ServerEventsHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.QuestDamageLedger;

@Mixin(value = ServerEventsHandler.class, remap = false)
public abstract class MixinServerEventsHandlerQuestDamage {
    @Invoker("doQuest")
    protected abstract void cnpcplus$doQuest(Player player, LivingEntity victim, boolean all);

    @Redirect(method = "invoke(Lnet/neoforged/neoforge/event/entity/living/LivingDeathEvent;)V", at = @At(value = "INVOKE", target = "Lnoppes/npcs/ServerEventsHandler;doQuest(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/LivingEntity;Z)V"), require = 1)
    private void cnpcplus$useDamageShare(ServerEventsHandler handler, Player player, LivingEntity victim, boolean all) {
        Player credited = QuestDamageLedger.evaluate(victim);
        cnpcplus$doQuest(credited == null ? player : credited, victim, all);
    }

    @Inject(method = "invoke(Lnet/neoforged/neoforge/event/entity/living/LivingDeathEvent;)V", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$creditOtherSources(LivingDeathEvent event, CallbackInfo ci) {
        if (event.getEntity().level().isClientSide) return;
        // Native player/NPC-owner calls already consumed the ledger above.
        Player credited = QuestDamageLedger.evaluate(event.getEntity());
        if (credited != null) cnpcplus$doQuest(credited, event.getEntity(), true);
    }

}
