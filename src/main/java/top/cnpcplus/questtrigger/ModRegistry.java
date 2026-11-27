package top.cnpcplus.questtrigger;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("cnpcplus");
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("cnpcplus");
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "cnpcplus");
    public static final net.neoforged.neoforge.registries.DeferredBlock<BlockQuestTrigger> QUEST_TRIGGER =
            BLOCKS.register("quest_trigger", BlockQuestTrigger::new);
    public static final java.util.function.Supplier<BlockItem> QUEST_TRIGGER_ITEM = ITEMS.register("quest_trigger",
            () -> new BlockItem(QUEST_TRIGGER.get(), new Item.Properties()));
    public static final java.util.function.Supplier<BlockEntityType<TileQuestTrigger>> TILE_QUEST_TRIGGER =
            TILES.register("quest_trigger", () -> BlockEntityType.Builder.of(TileQuestTrigger::new, QUEST_TRIGGER.get()).build(null));

    private ModRegistry() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TILES.register(bus);
    }
}
