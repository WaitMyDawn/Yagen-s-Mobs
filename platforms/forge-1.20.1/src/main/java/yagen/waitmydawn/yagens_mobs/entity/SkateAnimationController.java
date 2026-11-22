package yagen.waitmydawn.yagens_mobs.entity;

import java.util.Map;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.state.BoneSnapshot;

final class SkateAnimationController extends AnimationController<DesertSkate> {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.desert_skate.idle");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.desert_skate.glide");
    private static final RawAnimation DIVE = RawAnimation.begin().thenPlay("animation.desert_skate.dive");
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.desert_skate.emerge");
    private static final RawAnimation EAT = RawAnimation.begin().thenPlay("animation.desert_skate.eat");
    private static final RawAnimation STING = RawAnimation.begin().thenPlay("animation.desert_skate.sting");
    private RawAnimation clip = IDLE;
    private double actionTick = -1.0;

    SkateAnimationController(DesertSkate skate) {
        super(skate, "motion", 0, state -> PlayState.CONTINUE);
    }

    @Override
    public void process(CoreGeoModel<DesertSkate> model, AnimationState<DesertSkate> state, Map<String, CoreGeoBone> bones,
                        Map<String, BoneSnapshot> snapshots, double tick, boolean crashWhenBoneMissing) {
        clip = selectClip(state);
        super.process(model, state, bones, snapshots, tick, crashWhenBoneMissing);
    }

    @Override
    protected PlayState handleAnimationState(AnimationState<DesertSkate> state) {
        return state.setAndContinue(clip);
    }

    @Override
    protected double adjustTick(double tick) {
        double localTick = super.adjustTick(tick);
        // Keep GeckoLib's transition clock intact. Action playback follows the synchronized server timeline.
        return actionTick >= 0.0 && getAnimationState() == State.RUNNING ? actionTick : localTick;
    }

    private RawAnimation selectClip(AnimationState<DesertSkate> state) {
        DesertSkate skate = state.getAnimatable();
        float partialTick = state.getPartialTick();
        var phase = skate.burrowPhase();
        if (phase == DesertSkate.BurrowPhase.DIVING || phase == DesertSkate.BurrowPhase.EMERGING) {
            actionTick = skate.burrowElapsed(partialTick);
            return phase == DesertSkate.BurrowPhase.DIVING ? DIVE : EMERGE;
        }
        float sting = skate.stingElapsed(partialTick);
        if (sting >= 0.0F && sting < SkateSting.TOTAL_TICKS) {
            actionTick = sting;
            return STING;
        }
        float eating = skate.eatingElapsed(partialTick);
        if (eating >= 0.0F) {
            actionTick = eating;
            return EAT;
        }
        actionTick = -1.0;
        return state.isMoving() ? GLIDE : IDLE;
    }
}
