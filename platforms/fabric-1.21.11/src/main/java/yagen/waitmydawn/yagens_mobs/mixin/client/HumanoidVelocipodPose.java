package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.client.RiderMountMotion;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.entity.velocipod.Velocipod;

@Mixin(HumanoidModel.class)
abstract class HumanoidVelocipodPose {
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Unique private boolean yagensMobs$usedRiderPose;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("HEAD"))
    private void yagensMobs$clearRiderPose(HumanoidRenderState state, CallbackInfo callback) {
        var level = Minecraft.getInstance().level;
        if (!(state instanceof AvatarRenderState avatar) || level == null || !(level.getEntity(avatar.id) instanceof LivingEntity entity)) return;
        if (!yagensMobs$usedRiderPose) {
            return;
        }
        yagensMobs$usedRiderPose = false;
        body.resetPose();
        head.resetPose();
        rightArm.resetPose();
        leftArm.resetPose();
        rightLeg.resetPose();
        leftLeg.resetPose();
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void yagensMobs$balanceOnVelocipod(HumanoidRenderState state, CallbackInfo callback) {
        var level = Minecraft.getInstance().level;
        if (!(state instanceof AvatarRenderState avatar) || level == null || !(level.getEntity(avatar.id) instanceof LivingEntity entity)) return;
        if (!(entity instanceof Player player)) return;
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var motion = RiderMountMotion.sample(player, partialTick);
        if (motion == null) return;
        float weight = motion.weight(player.tickCount + (double) partialTick);
        yagensMobs$usedRiderPose = true;
        var pod = player.getVehicle() instanceof Velocipod insect ? insect : null;
        boolean braced = pod != null && (pod.flight() == Velocipod.Flight.TAKEOFF || pod.flight() == Velocipod.Flight.LAND);
        float step = Mth.sin(Mth.PI * weight);
        float lean = (braced ? .23F : .14F) * weight + .13F * step;
        float stride = (braced ? .27F : .20F) * weight + .32F * step;
        float stanceRoll = .045F * weight;
        float legLength = 12;
        float waist = 12;
        float hipY = 24 - legLength * Mth.cos(stride) * Mth.cos(stanceRoll);

        // Vanilla limbs are siblings of the torso. Tilt around the waist and place each joint
        // in the same torso space so that the skin and the copied armor layers stay connected.
        var torso = new Quaternionf().rotationX(lean);
        var waistOffset = torso.transform(new Vector3f(0, waist, 0));
        float anchorWeight = pod == null ? weight : 1;
        body.setPos(Mth.lerp(anchorWeight, body.x, -waistOffset.x),
                Mth.lerp(anchorWeight, body.y, hipY - waistOffset.y),
                Mth.lerp(anchorWeight, body.z, -waistOffset.z));
        body.setRotation(lean + (1 - anchorWeight) * body.xRot,
                (1 - anchorWeight) * body.yRot, (1 - anchorWeight) * body.zRot);
        head.setPos(Mth.lerp(anchorWeight, head.x, body.x), Mth.lerp(anchorWeight, head.y, body.y),
                Mth.lerp(anchorWeight, head.z, body.z));
        yagensMobs$attachJoint(rightArm, torso, rightArm.getInitialPose().x(), 2, 0, anchorWeight);
        yagensMobs$attachJoint(leftArm, torso, leftArm.getInitialPose().x(), 2, 0, anchorWeight);
        yagensMobs$attachJoint(rightLeg, torso, rightLeg.getInitialPose().x(), waist - .38F, 0, anchorWeight);
        yagensMobs$attachJoint(leftLeg, torso, leftLeg.getInitialPose().x(), waist - .38F, 0, anchorWeight);
        float nativeHeadYaw = head.yRot;
        float facing = Mth.wrapDegrees(player.getYHeadRot() - motion.heading()) * Mth.DEG_TO_RAD;
        head.yRot = nativeHeadYaw + weight * Mth.wrapDegrees((facing - nativeHeadYaw) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        // Bow and crossbow poses span both arms. Keep the vanilla pair and align it with the rider's gaze.
        if (player.isUsingItem() || player.swinging || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            if (player.isUsingItem() || player.swinging || yagensMobs$aimingCrossbow(state.leftArmPose, state.rightArmPose)) {
                float aim = head.yRot - nativeHeadYaw;
                rightArm.yRot += aim;
                leftArm.yRot += aim;
            }
            if (player.isUsingItem()) {
                switch (player.getUseItem().getUseAnimation()) {
                    case EAT, DRINK -> {
                        boolean right = (player.getMainArm() == HumanoidArm.RIGHT) == (player.getUsedItemHand() == InteractionHand.MAIN_HAND);
                        var arm = right ? rightArm : leftArm;
                        float bite = Mth.sin((player.getTicksUsingItem() + partialTick) * .85F);
                        arm.xRot = Mth.lerp(weight, arm.xRot, -1.50F + head.xRot * .55F + bite * .07F);
                        arm.yRot = Mth.lerp(weight, arm.yRot, head.yRot + (right ? -.28F : .28F));
                    }
                    default -> {}
                }
            }
        } else {
            leftArm.setRotation(Mth.lerp(weight, leftArm.xRot, -.23F + lean), -.06F * weight, -.47F * weight - .16F * step);
            rightArm.setRotation(Mth.lerp(weight, rightArm.xRot, .18F + lean), .08F * weight, .45F * weight + .16F * step);
        }
        rightLeg.setRotation(stride + (pod == null ? (1 - weight) * rightLeg.xRot : 0), -.09F * weight, stanceRoll);
        leftLeg.setRotation(-stride + (pod == null ? (1 - weight) * leftLeg.xRot : 0), .09F * weight, -stanceRoll);
    }

    @Unique
    private static boolean yagensMobs$aimingCrossbow(HumanoidModel.ArmPose left, HumanoidModel.ArmPose right) {
        return left == HumanoidModel.ArmPose.CROSSBOW_HOLD || right == HumanoidModel.ArmPose.CROSSBOW_HOLD;
    }

    @Unique
    private void yagensMobs$attachJoint(ModelPart part, Quaternionf torso, float x, float y, float z, float weight) {
        var joint = torso.transform(new Vector3f(x, y, z));
        part.setPos(Mth.lerp(weight, part.x, body.x + joint.x), Mth.lerp(weight, part.y, body.y + joint.y),
                Mth.lerp(weight, part.z, body.z + joint.z));
    }
}

