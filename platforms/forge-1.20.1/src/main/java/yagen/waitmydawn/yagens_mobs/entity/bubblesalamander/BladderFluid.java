package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;
import net.minecraft.world.level.block.Blocks;

/** A full-height, stationary swimming medium confined to the membrane block. */
@EventBusSubscriber(modid = ModIdentity.ID)
public final class BladderFluid extends Fluid {
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, ModIdentity.ID);
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, ModIdentity.ID);
    public static final RegistryObject<FluidType> TYPE = TYPES.register("bladder_medium",
            () -> new FluidType(FluidType.Properties.create().canDrown(false).canSwim(true)
                    .canExtinguish(true).fallDistanceModifier(0F).descriptionId("block.yagens_mobs.air_bladder")) {
                @Override public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions() {
                        public net.minecraft.resources.ResourceLocation getStillTexture() { return new net.minecraft.resources.ResourceLocation(ModIdentity.ID, "block/bladder/empty"); }
                        public net.minecraft.resources.ResourceLocation getFlowingTexture() { return getStillTexture(); }
                    });
                }
            });
    public static final RegistryObject<BladderFluid> MEDIUM = FLUIDS.register("bladder_medium", BladderFluid::new);

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
    }

    @SubscribeEvent
    public static void breathe(LivingBreatheEvent event) {
        if (event.getEntity().getEyeInFluidType() == TYPE.get()) {
            event.setCanBreathe(true);
            event.setCanRefillAir(true);
            event.setRefillAirAmount(4);
        }
    }

    @Override public FluidType getFluidType() { return TYPE.get(); }
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
