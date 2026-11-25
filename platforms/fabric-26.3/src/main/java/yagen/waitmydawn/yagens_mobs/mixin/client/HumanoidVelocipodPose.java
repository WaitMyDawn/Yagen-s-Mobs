package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
    @Shadow @Final public ModelPart hat;
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
        hat.resetPose();
        rightArm.resetPose();
        leftArm.resetPose();
        rightLeg.resetPose();
        leftLeg.resetPose();
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void yagensMobs$balanceOnVelocipod(HumanoidRenderState state, CallbackInfo callback) {
        var level = Minecraft.getInstance().level;
        if (!(state instanceof AvatarRenderState avatar) || level == null || !(level.getEntity(avatar.id) instanceof LivingEntity entity)) return;
        if (!(entity instanceof Player player) || !(player.getVehicle() instanceof Velocipod pod)) {
            return;
        }
        yagensMobs$usedRiderPose = true;
        var phase = pod.flight();
        boolean braced = phase == Velocipod.Flight.TAKEOFF || phase == Velocipod.Flight.LAND;
        float lean = braced ? .23F : .14F;
        float stride = braced ? .27F : .20F;
        float stanceRoll = .045F;
        float legLength = 12;
        float waist = 12;
        float hipY = 24 - legLength * Mth.cos(stride) * Mth.cos(stanceRoll);

        // Vanilla limbs are siblings of the torso. Tilt around the waist and place each joint
        // in the same torso space so that the skin and the copied armor layers stay connected.
        var torso = new Quaternionf().rotationX(lean);
        var waistOffset = torso.transform(new Vector3f(0, waist, 0));
        body.setPos(-waistOffset.x, hipY - waistOffset.y, -waistOffset.z);
        body.setRotation(lean, 0, 0);
        head.setPos(body.x, body.y, body.z);
        yagensMobs$attachJoint(rightArm, torso, rightArm.getInitialPose().x(), 2, 0);
        yagensMobs$attachJoint(leftArm, torso, leftArm.getInitialPose().x(), 2, 0);
        yagensMobs$attachJoint(rightLeg, torso, rightLeg.getInitialPose().x(), waist - .38F, 0);
        yagensMobs$attachJoint(leftLeg, torso, leftLeg.getInitialPose().x(), waist - .38F, 0);
        head.setRotation(Mth.clamp(state.xRot * Mth.DEG_TO_RAD - .08F, -.63F, .6F),
                Mth.clamp(Mth.wrapDegrees(player.getYHeadRot() - (pod.getYRot() + 52)) * Mth.DEG_TO_RAD, -1.2F, 1.2F),
                0);
        hat.loadPose(head.storePose());
        leftArm.setRotation(-.23F + lean, -.06F, -.47F);
        rightArm.setRotation(.18F + lean, .08F, .45F);
        rightLeg.setRotation(stride, -.09F, stanceRoll);
        leftLeg.setRotation(-stride, .09F, -stanceRoll);
    }

    @Unique
    private void yagensMobs$attachJoint(ModelPart part, Quaternionf torso, float x, float y, float z) {
        var joint = torso.transform(new Vector3f(x, y, z));
        part.setPos(body.x + joint.x, body.y + joint.y, body.z + joint.z);
    }
}

