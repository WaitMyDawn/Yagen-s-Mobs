package yagen.waitmydawn.yagens_mobs.silk;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import yagen.waitmydawn.yagens_mobs.registries.ModEntities;

public final class RockSilkGrip extends Entity {
    public static final int FORWARD = 1;
    public static final int BACK = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int SPRINT = 16;
    public static final int JUMP = 32;
    public static final int RELEASE = 64;

    private static final double STEP = 0.115;
    private static final double DESCENT_MULTIPLIER = 1.6;
    private static final double SPRINT_MULTIPLIER = 1.5;
    private static final double END_MARGIN = 0.08;
    private static final double GRIP_OFFSET = 0.39;
    private static final EntityDataAccessor<Boolean> MOVING =
            SynchedEntityData.defineId(RockSilkGrip.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<org.joml.Vector3fc> MODEL_UP =
            SynchedEntityData.defineId(RockSilkGrip.class, EntityDataSerializers.VECTOR3);

    private UUID lineId;
    private Vec3 side = Vec3.ZERO;
    private double travel;
    private int input;
    private int inputTick;

    public RockSilkGrip(EntityType<? extends RockSilkGrip> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    static void grab(ServerPlayer player, RockSilkInteractions.GripTarget target) {
        var level = player.level();
        RockSilkLine line = target.line();
        Vec3 from = line.first().tip();
        Vec3 span = line.tip().subtract(from);
        if (!line.supportsGrip() || span.length() <= END_MARGIN * 2) {
            return;
        }
        double distance = Mth.clamp(target.fraction() * span.length(), END_MARGIN, span.length() - END_MARGIN);
        Vec3 point = from.add(span.normalize().scale(distance));
        Vec3 horizontal = new Vec3(span.x, 0, span.z);
        Vec3 towardsPlayer = player.position().subtract(point).multiply(1, 0, 1);
        Vec3 lateral = horizontal.lengthSqr() > 1e-4
                ? horizontal.cross(new Vec3(0, 1, 0)).normalize()
                : towardsPlayer.normalize();
        if (lateral.lengthSqr() < 1e-4) {
            lateral = Vec3.directionFromRotation(0, player.getYRot()).scale(-1);
        }
        if (lateral.dot(towardsPlayer) < 0) {
            lateral = lateral.scale(-1);
        }

        Vec3 feet = feetAt(player, point, lateral, span.normalize().y);
        if (!roomFor(player, player.position(), feet)) {
            lateral = lateral.scale(-1);
            feet = feetAt(player, point, lateral, span.normalize().y);
            if (!roomFor(player, player.position(), feet)) {
                return;
            }
        }

        var grip = ModEntities.ROCK_SILK_GRIP.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (grip == null) {
            return;
        }
        grip.lineId = line.getUUID();
        grip.side = lateral;
        grip.travel = distance;
        Vec3 up = span.normalize();
        if (up.y < 0) {
            up = up.scale(-1);
        }
        grip.entityData.set(MODEL_UP, up.toVector3f());
        grip.snapTo(feet.x, feet.y, feet.z, (float) Math.toDegrees(Math.atan2(lateral.x, -lateral.z)), 0);
        if (!level.addFreshEntity(grip) || !player.startRiding(grip, true, false)) {
            grip.discard();
            return;
        }
        grip.positionRider(player);
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
    }

    void updateInput(ServerPlayer player, int keys) {
        if (getFirstPassenger() == player) {
            input = keys & (FORWARD | BACK | LEFT | RIGHT | SPRINT | JUMP | RELEASE);
            inputTick = tickCount;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            getInterpolation().interpolate();
            return;
        }

        if (!(getFirstPassenger() instanceof ServerPlayer player) || !player.isAlive()) {
            discard();
            return;
        }
        ServerLevel level = player.level();
        player.fallDistance = 0;
        if (tickCount - inputTick > 15) {
            input = 0;
        }
        if ((input & (RELEASE | JUMP)) != 0) {
            release(player, (input & RELEASE) == 0);
            return;
        }
        if (!(level.getEntity(lineId) instanceof RockSilkLine line) || !line.supportsGrip()) {
            release(player, false);
            return;
        }

        Vec3 start = line.first().tip();
        Vec3 span = line.tip().subtract(start);
        double length = span.length();
        Vec3 axis = span.normalize();
        double direction = direction(player, axis);
        double step = STEP * direction;
        if (axis.y * step < 0) {
            step *= DESCENT_MULTIPLIER;
        }
        if ((input & SPRINT) != 0) {
            step *= SPRINT_MULTIPLIER;
        }

        double next = Mth.clamp(travel + step, END_MARGIN, length - END_MARGIN);
        Vec3 feet = feetAt(player, start.add(axis.scale(next)), side, axis.y);
        boolean moving = Math.abs(next - travel) > 1e-5 && roomFor(player, position(), feet);
        entityData.set(MOVING, moving);
        if (moving) {
            travel = next;
            setPos(feet);
        }
    }

    private double direction(Player player, Vec3 axis) {
        double forward = pressed(FORWARD) - pressed(BACK);
        double strafe = pressed(LEFT) - pressed(RIGHT);
        if (axis.horizontalDistanceSqr() < 0.06) {
            return forward * Math.signum(axis.y);
        }

        Vec3 facing = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 left = new Vec3(facing.z, 0, -facing.x);
        Vec3 wanted = facing.scale(forward).add(left.scale(strafe)).normalize();
        Vec3 along = axis.multiply(1, 0, 1).normalize();
        double projection = wanted.dot(along);
        return Math.abs(projection) >= 0.2 ? Math.signum(projection) : 0;
    }

    private int pressed(int key) {
        return (input & key) == 0 ? 0 : 1;
    }

    private static Vec3 feetAt(Player player, Vec3 point, Vec3 side, double vertical) {
        float hang = hangAmount(vertical);
        return point.add(side.scale(GRIP_OFFSET * (1 - hang))).add(0, -holdHeight(player, hang), 0);
    }

    public static double holdHeight(Player player, float hanging) {
        return player.getDimensions(Pose.STANDING).height() * Mth.lerp(hanging, .69F, 1.07F);
    }

    private static float hangAmount(double vertical) {
        float slope = Mth.clamp((float) ((.9 - Math.abs(vertical)) / .55), 0, 1);
        return slope * slope * (3 - 2 * slope);
    }

    public float hangAmount() {
        return hangAmount(entityData.get(MODEL_UP).y());
    }

    public Vector3f modelUp() {
        var silk = entityData.get(MODEL_UP);
        return new Vector3f(silk).lerp(new Vector3f(0, 1, 0), hangAmount()).normalize();
    }

    private static boolean roomFor(Player player, Vec3 from, Vec3 to) {
        var level = player.level();
        // Test the swept standing box even when the client has sent sneak or a swimming pose.
        AABB swept = player.getDimensions(Pose.STANDING).makeBoundingBox(from)
                .expandTowards(to.subtract(from)).deflate(1e-5);
        return swept.minY >= level.getMinY() && swept.maxY <= level.getMaxY() + 1
                && level.hasChunksAt(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                        BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))
                && level.getWorldBorder().isWithinBounds(swept)
                && !level.getBlockCollisions(player, swept).iterator().hasNext();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (passenger instanceof ServerPlayer player) {
            // Vanilla updates the server's held jump flag only while the player is a passenger.
            // Drop the last riding input before normal ground-jump processing resumes.
            player.setJumping(false);
        }
    }

    private void release(ServerPlayer player, boolean jump) {
        player.stopRiding();
        if (player.getVehicle() == this) {
            return;
        }
        if (jump) {
            player.setOnGround(false);
            player.setDeltaMovement(side.scale(0.29).add(0, 0.29, 0));
            player.syncVelocity = true;
        }
        discard();
    }

    public boolean isClimbing() {
        return entityData.get(MOVING);
    }

    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction move) {
        move.accept(passenger, getX(), getY(), getZ());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return position();
    }

    @Override
    public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MOVING, false).define(MODEL_UP, new Vector3f(0, 1, 0));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
    }
}

