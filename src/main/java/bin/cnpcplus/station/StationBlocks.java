package bin.cnpcplus.station;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.smelting.SmeltingGuiOpener;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.*;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import javax.annotation.Nullable;

/** Backports actual work blocks, not just recipe editors, to the legacy block registry. */
@Mod.EventBusSubscriber(modid = CnpcPlus.MODID)
public final class StationBlocks extends BlockHorizontal {
    public static final StationBlocks GRINDSTONE = new StationBlocks(false);
    public static final StationBlocks CAMPFIRE = new StationBlocks(true);
    private final boolean campfire;
    private StationBlocks(boolean campfire) {
        super(campfire ? Material.WOOD : Material.ROCK); this.campfire = campfire;
        String id = campfire ? "campfire" : "grindstone";
        setRegistryName(CnpcPlus.MODID, id); setTranslationKey("cnpcplus." + id);
        setCreativeTab(CreativeTabs.DECORATIONS); setHardness(2); setSoundType(campfire ? SoundType.WOOD : SoundType.STONE);
        if (campfire) setLightLevel(1);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> event) { event.getRegistry().registerAll(GRINDSTONE, CAMPFIRE); }
    @SubscribeEvent public static void items(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(new ItemBlock(GRINDSTONE).setRegistryName(GRINDSTONE.getRegistryName()),
                new ItemBlock(CAMPFIRE).setRegistryName(CAMPFIRE.getRegistryName()));
    }
    @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, FACING); }
    @Override public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)); }
    @Override public int getMetaFromState(IBlockState state) { return state.getValue(FACING).getHorizontalIndex(); }
    @Override public IBlockState withRotation(IBlockState state, Rotation rotation) { return state.withProperty(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public IBlockState withMirror(IBlockState state, Mirror mirror) { return withRotation(state, mirror.toRotation(state.getValue(FACING))); }
    @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing face, float x, float y, float z, int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }
    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state) { return false; }
    @Override public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (campfire) return new AxisAlignedBB(0, 0, 0, 1, .4375, 1);
        return state.getValue(FACING).getAxis() == EnumFacing.Axis.X
                ? new AxisAlignedBB(.25, 0, .125, .75, 1, .875) : new AxisAlignedBB(.125, 0, .25, .875, 1, .75);
    }
    @Override public boolean hasTileEntity(IBlockState state) { return campfire; }
    @Override @Nullable public TileEntity createTileEntity(World world, IBlockState state) { return campfire ? new CampfireTile() : null; }
    @Override public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player,
            EnumHand hand, EnumFacing face, float x, float y, float z) {
        if (!campfire) {
            if (!world.isRemote) SmeltingGuiOpener.openGrindstone((EntityPlayerMP)player, pos);
            return true;
        }
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof CampfireTile && ((CampfireTile)tile).place(player, hand);
    }
    @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = world.getTileEntity(pos);
        if (!world.isRemote && tile instanceof CampfireTile) for (ItemStack stack : ((CampfireTile)tile).items)
            net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
        super.breakBlock(world, pos, state);
    }
    @Override public void onEntityWalk(World world, BlockPos pos, Entity entity) {
        if (campfire && !entity.isImmuneToFire()) entity.attackEntityFrom(DamageSource.IN_FIRE, 1);
        super.onEntityWalk(world, pos, entity);
    }
    @Override @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public void randomDisplayTick(IBlockState state, World world, BlockPos pos, java.util.Random random) {
        if (!campfire) return;
        world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, pos.getX() + .5, pos.getY() + .6, pos.getZ() + .5, 0, .03, 0);
        if (random.nextInt(10) == 0) world.playSound(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                net.minecraft.init.SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, .5f, 1, false);
    }
}
