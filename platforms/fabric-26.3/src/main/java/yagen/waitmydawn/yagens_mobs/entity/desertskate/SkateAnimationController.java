package yagen.waitmydawn.yagens_mobs.entity.desertskate;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.state.ControllerState;
import com.geckolib.loading.math.MolangQueries;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;

final class SkateAnimationController extends AnimationController<DesertSkate> {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.desert_skate.idle");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.desert_skate.glide");
    private static final RawAnimation DIVE = RawAnimation.begin().thenPlay("animation.desert_skate.dive");
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.desert_skate.emerge");
    private static final RawAnimation EAT = RawAnimation.begin().thenPlay("animation.desert_skate.eat");
    private static final RawAnimation STING = RawAnimation.begin().thenPlay("animation.desert_skate.sting");

    SkateAnimationController(DesertSkate skate) {
        super("motion", 0, state -> {
            var action = action(state.animatable(), state.renderState().getPartialTick());
            return state.setAndContinue(action == null ? state.isMoving() ? GLIDE : IDLE : action.clip());
        });
    }

    @Override
    protected void progressExistingAnimation(DesertSkate skate, GeoRenderState state,
                                             double age, double delta) {
        super.progressExistingAnimation(skate, state, age, delta);
        var action = action(skate, state.getPartialTick());
        if (action != null && action.clip().equals(getCurrentRawAnimation())) {
            // Combat timings are synchronized in Minecraft ticks. GeckoLib 5 uses seconds for timeline sampling.
            setAnimationTime(Math.max(0, action.ticks()) / 20.0);
        }
    }

    private static Action action(DesertSkate skate, float partialTick) {
        var phase = skate.burrowPhase();
        if (phase == DesertSkate.BurrowPhase.DIVING || phase == DesertSkate.BurrowPhase.EMERGING) {
            return new Action(phase == DesertSkate.BurrowPhase.DIVING ? DIVE : EMERGE, skate.burrowElapsed(partialTick));
        }
        float sting = skate.stingElapsed(partialTick);
        if (sting >= 0 && sting < SkateSting.TOTAL_TICKS) {
            return new Action(STING, sting);
        }
        float eating = skate.eatingElapsed(partialTick);
        return eating < 0 ? null : new Action(EAT, eating);
    }

    private record Action(RawAnimation clip, float ticks) {}
}
