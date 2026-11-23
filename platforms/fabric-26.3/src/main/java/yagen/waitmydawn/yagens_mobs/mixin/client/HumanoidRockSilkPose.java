package yagen.waitmydawn.yagens_mobs.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
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

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void yagensMobs$holdRockSilk(HumanoidRenderState state, CallbackInfo callback) {
        var level = Minecraft.getInstance().level;
        if (!(state instanceof AvatarRenderState avatar) || level == null || !(level.getEntity(avatar.id) instanceof LivingEntity entity)) return;
        if (!(entity instanceof Player) || !(entity.getVehicle() instanceof RockSilkGrip grip)) {
            return;
        }
        float stride = grip.isClimbing() ? Mth.sin(state.ageInTicks * 0.47F) * 0.19F : 0;
        float hanging = grip.hangAmount();
        body.xRot = Mth.lerp(hanging, .075F, 0);
        head.yRot = Mth.clamp(Mth.wrapDegrees(entity.getYHeadRot() - grip.getYRot()), -65, 65) * Mth.DEG_TO_RAD;
        head.xRot = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -0.85F, 0.64F);
        hat.loadPose(head.storePose());

        rightArm.setRotation(Mth.lerp(hanging, -1.71F, -3.06F) + stride, -.56F * (1 - hanging), -.08F);
        leftArm.setRotation(Mth.lerp(hanging, -1.53F, -3.06F) - stride, .56F * (1 - hanging), .08F);
        rightLeg.setRotation(Mth.lerp(hanging, -1.14F, .16F) - stride * .65F, -.36F * (1 - hanging), .24F - .18F * hanging);
        leftLeg.setRotation(Mth.lerp(hanging, -1.02F, .23F) + stride * .65F, .36F * (1 - hanging), -.24F + .18F * hanging);
    }
}
