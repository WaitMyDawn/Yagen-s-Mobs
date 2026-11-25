package yagen.waitmydawn.yagens_mobs.entity.salvage.client.mixin;

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
import yagen.waitmydawn.yagens_mobs.entity.salvage.SalvageRay;

@Mixin(HumanoidModel.class)
abstract class SalvageRiderPose {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void yagensMobs$hangUnderRay(HumanoidRenderState state, CallbackInfo callback) {
        var level = Minecraft.getInstance().level;
        if (!(state instanceof AvatarRenderState avatar) || level == null || !(level.getEntity(avatar.id) instanceof LivingEntity entity)) return;
        if (entity instanceof Player && entity.getVehicle() instanceof SalvageRay ray) {
            float drift = ray.flightPitch(1) * Mth.DEG_TO_RAD;
            rightArm.setRotation(-Mth.PI + .28F, 0, -.025F);
            leftArm.setRotation(-Mth.PI + .28F, 0, .025F);
            rightLeg.setRotation(.19F + .22F * drift, -.05F, .05F);
            leftLeg.setRotation(.25F + .22F * drift, .05F, -.05F);
        }
    }
}
