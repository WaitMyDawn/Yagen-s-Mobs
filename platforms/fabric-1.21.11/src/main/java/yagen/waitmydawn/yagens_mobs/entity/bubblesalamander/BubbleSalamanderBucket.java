package yagen.waitmydawn.yagens_mobs.entity.bubblesalamander;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

public final class BubbleSalamanderBucket extends MobBucketItem {
    public BubbleSalamanderBucket(Properties properties) {
        super(ModEntities.BUBBLE_SALAMANDER.get(), Fluids.WATER, SoundEvents.BUCKET_EMPTY_FISH, properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        var hit = getPlayerPOVHitResult(level, player,
                BubbleSalamander.eyeInWater(player) ? ClipContext.Fluid.NONE : ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }
        BlockPos clicked = hit.getBlockPos();
        boolean inWater = level.getFluidState(clicked).is(FluidTags.WATER);
        BlockPos pos = inWater ? clicked : clicked.relative(hit.getDirection());
        if (level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.WATER_EVAPORATES, pos) || !level.mayInteract(player, clicked) || !level.mayInteract(player, pos)
                || !player.mayUseItemAt(pos, hit.getDirection(), stack)) {
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }

        var mob = ModEntities.BUBBLE_SALAMANDER.get().create(server, net.minecraft.world.entity.EntitySpawnReason.BUCKET);
        if (mob == null) {
            return InteractionResult.FAIL;
        }
        mob.snapTo(pos.getX() + .5, pos.getY() + .05, pos.getZ() + .5, player.getYRot(), 0);
        if (!server.noCollision(mob)) {
            return InteractionResult.FAIL;
        }
        mob.loadFromBucketTag(stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).copyTag());
        mob.setFromBucket(true);
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            mob.setCustomName(stack.get(DataComponents.CUSTOM_NAME));
        }
        // Commit the item exchange only once a mob exists and the water placement has succeeded.
        if (!server.addFreshEntity(mob)) {
            return InteractionResult.FAIL;
        }
        if (server.getFluidState(pos).is(FluidTags.WATER)) {
            playEmptySound(player, level, pos);
        } else if (!emptyContents(player, level, pos, null)) {
            mob.discard();
            return InteractionResult.FAIL;
        }

        level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.PLACED_BLOCK.trigger(serverPlayer, pos, stack);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResult.CONSUME.heldItemTransformedTo(getEmptySuccessItem(stack, player));
    }
}
