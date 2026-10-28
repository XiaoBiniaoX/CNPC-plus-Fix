package bin.cnpcplus.crafting;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.smelting.SmeltingGuiOpener;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** 1.12.2 没有原版锻造台，提供与三输入锻造配方对应的实际工作台。 */
@Mod.EventBusSubscriber(modid = CnpcPlus.MODID)
public final class SmithingTable extends Block {
    public static final SmithingTable TABLE = new SmithingTable();
    private SmithingTable() {
        super(Material.WOOD);
        setRegistryName(CnpcPlus.MODID, "smithing_table");
        setTranslationKey("cnpcplus.smithing_table");
        setCreativeTab(CreativeTabs.DECORATIONS);
        setHardness(2.5F);
        setSoundType(SoundType.WOOD);
    }
    @SubscribeEvent public static void registerBlocks(RegistryEvent.Register<Block> event) { event.getRegistry().register(TABLE); }
    @SubscribeEvent public static void registerItems(RegistryEvent.Register<Item> event) { event.getRegistry().register(new ItemBlock(TABLE).setRegistryName(TABLE.getRegistryName())); }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
                                              EnumHand hand, EnumFacing facing, float x, float y, float z) {
        if (!world.isRemote && player instanceof EntityPlayerMP) SmeltingGuiOpener.openSmithing((EntityPlayerMP) player, pos);
        return true;
    }
}
