package top.cnpcplus.questtrigger;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import top.cnpcplus.CnpcPlus;

public class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CnpcPlus.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CnpcPlus.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CnpcPlus.MOD_ID);

    public static final RegistryObject<BlockQuestTrigger> QUEST_TRIGGER =
            BLOCKS.register("quest_trigger", BlockQuestTrigger::new);
    public static final RegistryObject<Item> QUEST_TRIGGER_ITEM =
            ITEMS.register("quest_trigger", () -> new BlockItem(QUEST_TRIGGER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<TileQuestTrigger>> TILE_QUEST_TRIGGER =
            TILES.register("quest_trigger", () -> BlockEntityType.Builder.of(TileQuestTrigger::new, QUEST_TRIGGER.get()).build(null));

    public static void register() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TILES.register(bus);
    }
}
