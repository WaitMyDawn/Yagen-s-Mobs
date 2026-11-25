package yagen.waitmydawn.yagens_mobs.entity.desertskate;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

public final class SkateSpike extends Projectile {
    public static final double RANGE = 8.0;
    private static final float DAMAGE = 4.0F;
    private double remainingRange = RANGE;

    public SkateSpike(EntityType<? extends SkateSpike> type, Level level) {
        super(type, level);
    }

    public static void fire(ServerLevel level, DesertSkate shooter, Vec3 origin, Vec3 aim) {
        var spike = new SkateSpike(ModEntities.SKATE_SPIKE.get(), level);
        spike.setOwner(shooter);
        spike.setPos(origin);
        spike.shoot(aim.x, aim.y, aim.z, 1.6F, 0.75F);
        level.addFreshEntity(spike);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = getDeltaMovement();
        double speed = velocity.length();
        if (speed < 1.0E-6 || remainingRange <= 0) {
            discard();
            return;
        }
        // Clamp the final collision ray as well as the movement so a spike cannot hit beyond its range.
        double distance = Math.min(speed, remainingRange);
        if (!level().isClientSide) {
            setDeltaMovement(velocity.scale(distance / speed));
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !EventHooks.onProjectileImpact(this, hit)) {
                remainingRange -= distance;
                if (hitTargetOrDeflectSelf(hit) == ProjectileDeflection.NONE) {
                    discard();
                }
                return;
            }
        }
        updateRotation();
        setPos(position().add(velocity.scale(distance / speed)));
        remainingRange -= distance;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return !(target instanceof DesertSkate) && target != getOwner() && super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (hit.getEntity() instanceof LivingEntity target) {
            var owner = getOwner() instanceof LivingEntity living ? living : null;
            target.hurt(damageSources().mobProjectile(this, owner), DAMAGE);
        }
    }
}
