package top.cnpcplus;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityDispatcher;
import net.minecraftforge.common.capabilities.CapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.cnpcplus.anvil.AnvilCraftHandler;
import top.cnpcplus.anvil.AnvilRecipeData;
import top.cnpcplus.anvil.client.AnvilClientData;
import top.cnpcplus.drop.CraftPlan;
import top.cnpcplus.drop.DropRecipeData;
import top.cnpcplus.smithing.CustomSmithingRecipe;
import top.cnpcplus.smithing.SmithingRecipeData;

/** 同物品 ID、不同普通 Tag/ForgeCaps 的三类自定义配方行为回归。 */
public final class RecipeNbtRegression {
    private static final ResourceLocation CAP_ID = new ResourceLocation("cnpcplus", "recipe_nbt_regression");
    private static final Capability<IItemHandler> TEST_CAPABILITY = createTestCapability();
    private static final sun.misc.Unsafe UNSAFE;

    static {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private RecipeNbtRegression() {}

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        bootstrapForEntities();

        smithingPreservesConfiguredResult();
        anvilPreservesConfiguredResult();
        dropCraftPreservesConfiguredResult();
        System.out.println("RECIPE_NBT_REGRESSION=PASS");
    }

    private static void smithingPreservesConfiguredResult() {
        SmithingRecipeData data = new SmithingRecipeData();
        data.id = 901;
        data.base = variant("smithing_input", 11, 1);
        data.result = variant("smithing_result", 12, 1);

        SmithingRecipeData restored = SmithingRecipeData.fromNBT(data.toNBT());
        rehydrateCapabilities(restored.base);
        rehydrateCapabilities(restored.result);
        CustomSmithingRecipe recipe = new CustomSmithingRecipe(
                new ResourceLocation("cnpcplus", "smithing/901"),
                ItemStack.EMPTY, restored.base, ItemStack.EMPTY, restored.result, false);

        SimpleContainer slots = new SimpleContainer(3);
        slots.setItem(1, variant("wrong_same_id", 11, 1));
        check(!recipe.matches(slots, null), "smithing accepted same id with wrong ordinary tag");
        slots.setItem(1, variant("smithing_input", 10, 1));
        check(!recipe.matches(slots, null), "smithing accepted same id/tag with wrong ForgeCaps");
        slots.setItem(1, variant("smithing_input", 11, 1));
        check(recipe.matches(slots, null), "smithing rejected exact tag/cap input");

        ItemStack output = recipe.assemble(slots, null);
        assertExact(restored.result, output, "smithing output");
        check("smithing_result".equals(output.getTag().getString("Model")),
                "smithing copied the base tag over the configured result");
        proveIndependent(output, restored.result, "smithing output");
        System.out.println("PASS: smithing configured result keeps ordinary Tag and ForgeCaps");
    }

    private static void anvilPreservesConfiguredResult() throws Exception {
        AnvilRecipeData data = new AnvilRecipeData();
        data.id = 902;
        data.inputA = variant("anvil_input_a", 21, 1);
        data.inputB = variant("anvil_input_b", 22, 1);
        data.result = variant("anvil_result", 23, 1);
        AnvilRecipeData restored = AnvilRecipeData.fromNBT(data.toNBT());
        rehydrateCapabilities(restored.inputA);
        rehydrateCapabilities(restored.inputB);
        rehydrateCapabilities(restored.result);
        AnvilClientData.set(List.of(restored));

        Player player = shell(TestPlayer.class);
        ServerLevel level = shell(ServerLevel.class);
        setBoolean(Level.class.getDeclaredField("isClientSide"), level, true);
        setField(Entity.class, player, "level", level);

        AnvilUpdateEvent wrong = new AnvilUpdateEvent(
                variant("anvil_input_a", 20, 1), variant("anvil_input_b", 22, 1), "", 0, player);
        AnvilCraftHandler.onAnvilUpdate(wrong);
        check(wrong.getOutput().isEmpty(), "anvil accepted same id/tag with wrong ForgeCaps");

        AnvilUpdateEvent exact = new AnvilUpdateEvent(
                variant("anvil_input_a", 21, 1), variant("anvil_input_b", 22, 1), "", 0, player);
        AnvilCraftHandler.onAnvilUpdate(exact);
        assertExact(restored.result, exact.getOutput(), "anvil output");
        proveIndependent(exact.getOutput(), restored.result, "anvil output");
        System.out.println("PASS: anvil strict match and output keep ordinary Tag and ForgeCaps");
    }

    private static void dropCraftPreservesConfiguredResult() throws Exception {
        DropRecipeData data = new DropRecipeData();
        data.id = 903;
        data.inputs[0] = variant("drop_input", 31, 2);
        data.result = variant("drop_result", 32, 1);
        DropRecipeData restored = DropRecipeData.fromNBT(data.toNBT());
        rehydrateCapabilities(restored.inputs[0]);
        rehydrateCapabilities(restored.result);

        TestItemEntity wrong = shell(TestItemEntity.class);
        wrong.stack = variant("drop_input", 30, 4);
        TestItemEntity exact = shell(TestItemEntity.class);
        exact.stack = variant("drop_input", 31, 3);
        CraftPlan plan = CraftPlan.plan(List.of(wrong, exact), restored);
        check(plan != null, "drop recipe did not produce a plan");
        check(!plan.deduct.containsKey(wrong) && plan.deduct.get(exact) == 2,
                "drop strict matching did not distinguish ForgeCaps on the same item id");
        assertExact(restored.result, plan.output, "drop output");
        proveIndependent(plan.output, restored.result, "drop output");
        System.out.println("PASS: drop strict match and output keep ordinary Tag and ForgeCaps");
    }

    private static ItemStack variant(String model, int capabilityMarker, int count) {
        ItemStack stack = new ItemStack(Items.PAPER, count);
        stack.getOrCreateTag().putString("Model", model);
        installCapabilities(stack, new TestCapabilityProvider());
        IItemHandler handler = stack.getCapability(TEST_CAPABILITY)
                .orElseThrow(() -> new AssertionError("test ForgeCaps provider was not attached"));
        ((IItemHandlerModifiable) handler).setStackInSlot(0, new ItemStack(Items.DIAMOND, capabilityMarker));
        return stack;
    }

    /**
     * JavaExec 没有 ModLauncher 的 Event 构造器转换，不能走 AttachCapabilitiesEvent。
     * 测试直接安装 Forge 自己的 CapabilityDispatcher；生产 ItemStack copy/save/of
     * 仍走同一个 serializeCaps/deserializeCaps 实现。
     */
    private static void installCapabilities(ItemStack stack, TestCapabilityProvider provider) {
        try {
            CapabilityDispatcher dispatcher = new CapabilityDispatcher(
                    Map.of(CAP_ID, provider), List.of());
            Field capabilities = CapabilityProvider.class.getDeclaredField("capabilities");
            capabilities.setAccessible(true);
            capabilities.set(stack, dispatcher);
            Field initialized = CapabilityProvider.class.getDeclaredField("initialized");
            initialized.setAccessible(true);
            initialized.setBoolean(stack, true);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not install test ForgeCaps provider", e);
        }
    }

    private static void rehydrateCapabilities(ItemStack stack) {
        CompoundTag serialized = stack.save(new CompoundTag());
        CompoundTag allCaps = serialized.getCompound("ForgeCaps");
        TestCapabilityProvider provider = new TestCapabilityProvider();
        provider.deserializeNBT(allCaps.getCompound(CAP_ID.toString()));
        installCapabilities(stack, provider);
    }

    @SuppressWarnings("unchecked")
    private static Capability<IItemHandler> createTestCapability() {
        try {
            java.lang.reflect.Constructor<?> constructor = Capability.class.getDeclaredConstructor(String.class);
            constructor.setAccessible(true);
            return (Capability<IItemHandler>) constructor.newInstance("cnpcplus.recipe_nbt_regression");
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void assertExact(ItemStack expected, ItemStack actual, String label) {
        check(expected.getItem() == actual.getItem() && expected.getCount() == actual.getCount(),
                label + " changed item id/count");
        check(expected.save(new CompoundTag()).equals(actual.save(new CompoundTag())),
                label + " changed ordinary Tag or ForgeCaps");
        CompoundTag serialized = actual.save(new CompoundTag());
        check(serialized.contains("tag") && serialized.contains("ForgeCaps"),
                label + " did not serialize both ordinary Tag and ForgeCaps");
    }

    private static void proveIndependent(ItemStack output, ItemStack configured, String label) {
        output.getOrCreateTag().putString("Model", "mutated_output");
        check(!"mutated_output".equals(configured.getTag().getString("Model")),
                label + " shares mutable tag state with the configured recipe");
    }

    private static void bootstrapForEntities() {
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException)
                    || !"net.minecraftforge.network.NetworkEvent.<init>()".equals(cause.getMessage())) {
                throw unavailable;
            }
            System.out.println("HEADLESS_ONLY: skipped untransformed Forge network hook; not a loader smoke test");
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T shell(Class<T> type) throws InstantiationException {
        return (T) UNSAFE.allocateInstance(type);
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void setBoolean(Field field, Object target, boolean value) {
        UNSAFE.putBoolean(target, UNSAFE.objectFieldOffset(field), value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class TestCapabilityProvider implements ICapabilitySerializable<CompoundTag> {
        private final ItemStackHandler inventory = new ItemStackHandler(1);
        private final LazyOptional<IItemHandler> optional = LazyOptional.of(() -> this.inventory);

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                          @Nullable net.minecraft.core.Direction side) {
            return capability == TEST_CAPABILITY ? this.optional.cast() : LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return this.inventory.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            this.inventory.deserializeNBT(nbt);
        }
    }

    private static final class TestPlayer extends Player {
        private TestPlayer() {
            super(null, BlockPos.ZERO, 0.0F, new GameProfile(UUID.randomUUID(), "recipe-nbt-test"));
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }

    private static final class TestItemEntity extends ItemEntity {
        private ItemStack stack;

        private TestItemEntity() {
            super((EntityType<? extends ItemEntity>) null, null);
        }

        @Override
        public ItemStack getItem() {
            return this.stack;
        }
    }
}
