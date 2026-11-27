package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import net.minecraft.world.level.block.Blocks;

/** A full-height, stationary swimming medium confined to the membrane block. */
public final class BladderFluid extends Fluid {
    public static final Fluid MEDIUM = net.minecraft.core.Registry.register(
            net.minecraft.core.registries.BuiltInRegistries.FLUID,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ModIdentity.ID, "bladder_medium"), new BladderFluid());

    public static void register() {}

    @Override public float getOwnHeight(FluidState state) { return 1F; }
    @Override public float getHeight(FluidState state, BlockGetter level, BlockPos pos) { return 1F; }
    @Override public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) { return Shapes.block(); }
    @Override public boolean isSource(FluidState state) { return true; }
    @Override public int getAmount(FluidState state) { return 8; }
    @Override public int getTickDelay(LevelReader level) { return 0; }
    @Override public Item getBucket() { return Items.AIR; }
    @Override protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) { return false; }
    @Override protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) { return Vec3.ZERO; }
    @Override protected float getExplosionResistance() { return .25F; }
    @Override protected BlockState createLegacyBlock(FluidState state) { return Blocks.AIR.defaultBlockState(); }
}
