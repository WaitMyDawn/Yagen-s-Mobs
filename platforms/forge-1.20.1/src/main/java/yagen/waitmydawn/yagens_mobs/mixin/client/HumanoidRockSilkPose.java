package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import yagen.waitmydawn.yagens_mobs.silk.RockSilkGrip;

@Mixin(HumanoidModel.class)
abstract class HumanoidRockSilkPose {
    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void yagensMobs$holdRockSilk(LivingEntity entity, float limbSwing, float limbStrength, float age,
                                       float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (!(entity instanceof Player) || !(entity.getVehicle() instanceof RockSilkGrip grip)) {
            return;
        }
        float stride = grip.isClimbing() ? Mth.sin(age * 0.47F) * 0.19F : 0;
        body.xRot = 0.075F;
        head.yRot = Mth.clamp(Mth.wrapDegrees(entity.getYHeadRot() - grip.getYRot()), -65, 65) * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(headPitch * Mth.DEG_TO_RAD, -0.85F, 0.64F);
        hat.copyFrom(head);

        rightArm.setRotation(-1.71F + stride, -0.56F, -0.08F);
        leftArm.setRotation(-1.53F - stride, 0.56F, 0.08F);
        rightLeg.setRotation(-1.14F - stride * 0.65F, -0.36F, 0.24F);
        leftLeg.setRotation(-1.02F + stride * 0.65F, 0.36F, -0.24F);
    }
}

