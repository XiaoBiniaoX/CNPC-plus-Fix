package top.cnpcplus.mixin;

import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import top.cnpcplus.station.*;

@Mixin(CampfireBlockEntity.class)
public class MixinCampfireRecipes {
    @Shadow @Final private NonNullList<ItemStack> items;
    @Shadow @Final private int[] cookingProgress;
    @Shadow @Final private int[] cookingTime;
    @Unique private final UUID[] cnpcplus$owners = new UUID[4];
    @Unique private final int[] cnpcplus$recipes = new int[4];

    @Inject(method = "<init>", at = @At("TAIL"), require = 1)
    private void cnpcplus$init(BlockPos pos, BlockState state, CallbackInfo ci) { java.util.Arrays.fill(cnpcplus$recipes, -1); }

    @Inject(method = "placeFood", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$place(Entity entity, ItemStack stack, int time, CallbackInfoReturnable<Boolean> cir) {
        Player player = entity instanceof Player p ? p : null;
        CampfireBlockEntity block = (CampfireBlockEntity)(Object)this;
        boolean custom = StationRecipes.list(2, block.getLevel().isClientSide).stream().anyMatch(r -> r.matchesInputs(new ItemStack[]{stack}));
        StationRecipe recipe = custom ? StationRecipes.find(2, new ItemStack[]{stack}, player) : null;
        if (custom && recipe == null) { cir.setReturnValue(false); return; }
        for (int i = 0; i < 4; i++) if (items.get(i).isEmpty()) {
            cnpcplus$owners[i] = player == null ? null : player.getUUID();
            cnpcplus$recipes[i] = recipe == null ? -1 : recipe.id;
            break;
        }
    }
    @Inject(method = "cookTick", at = @At("HEAD"), cancellable = true, require = 1)
    private static void cnpcplus$cook(Level level, BlockPos pos, BlockState state, CampfireBlockEntity block, CallbackInfo ci) {
        MixinCampfireRecipes self = (MixinCampfireRecipes)(Object)block;
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            ItemStack input = self.items.get(i);
            if (input.isEmpty()) continue;
            Player owner = self.cnpcplus$owners[i] == null ? null : level.getServer().getPlayerList().getPlayer(self.cnpcplus$owners[i]);
            boolean custom = self.cnpcplus$recipes[i] >= 0 || StationRecipes.hasMatch(2, new ItemStack[]{input});
            StationRecipe recipe = custom ? StationRecipes.find(2, new ItemStack[]{input}, owner) : null;
            if (custom && recipe == null) continue;
            if (recipe != null) {
                if (self.cnpcplus$recipes[i] != recipe.id) self.cookingProgress[i] = 0;
                self.cnpcplus$recipes[i] = recipe.id;
                self.cookingTime[i] = recipe.time;
            }
            changed = true;
            if (++self.cookingProgress[i] < self.cookingTime[i]) continue;
            SimpleContainer container = new SimpleContainer(input);
            ItemStack output = recipe == null ? level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, container, level)
                    .map(r -> r.assemble(container, level.registryAccess())).orElse(input.copy()) : recipe.outputs[0].copy();
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), output);
            self.items.set(i, ItemStack.EMPTY);
            self.cnpcplus$owners[i] = null; self.cnpcplus$recipes[i] = -1;
            level.sendBlockUpdated(pos, state, state, 3);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
        }
        if (changed) block.setChanged();
        ci.cancel();
    }
    @Inject(method = "load", at = @At("TAIL"), require = 1)
    private void cnpcplus$load(CompoundTag tag, CallbackInfo ci) {
        for (int i = 0; i < 4; i++) {
            cnpcplus$owners[i] = tag.hasUUID("CNPCPlusOwner" + i) ? tag.getUUID("CNPCPlusOwner" + i) : null;
            cnpcplus$recipes[i] = tag.contains("CNPCPlusRecipe" + i) ? tag.getInt("CNPCPlusRecipe" + i) : -1;
        }
    }
    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 1)
    private void cnpcplus$save(CompoundTag tag, CallbackInfo ci) {
        for (int i = 0; i < 4; i++) {
            if (cnpcplus$owners[i] != null) tag.putUUID("CNPCPlusOwner" + i, cnpcplus$owners[i]);
            tag.putInt("CNPCPlusRecipe" + i, cnpcplus$recipes[i]);
        }
    }
}
