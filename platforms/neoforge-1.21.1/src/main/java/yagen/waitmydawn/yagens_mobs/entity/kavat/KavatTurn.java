package yagen.waitmydawn.yagens_mobs.entity.kavat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Tick-based visual motion in degrees. All joint offsets are relative to their animated parents. */
final class KavatTurn {
    private static final int LINKS = 12;
    private static final float RAD = (float) (Math.PI / 180);
    private final float[] bends = new float[LINKS + 1];
    private final float[] previousBends = bends.clone();
    private float heading, previousHeading, speed, lead, previousLead;
    private float phase, previousPhase, steps, previousSteps;
    private boolean initialized;

    void tick(float target, boolean pivotOnGround, boolean reset) {
        if (reset || !initialized) {
            initialized = true;
            previousHeading = heading = target;
            previousLead = lead = speed = previousSteps = steps = previousPhase = phase = 0;
            Arrays.fill(bends, 0);
            Arrays.fill(previousBends, 0);
            return;
        }
        previousHeading = heading;
        previousLead = lead;
        previousSteps = steps;
        previousPhase = phase;
        System.arraycopy(bends, 0, previousBends, 0, bends.length);
        float error = (target - heading) % 360;
        if (error > 180) error -= 360;
        if (error < -180) error += 360;
        if (Math.abs(error) > 178 && Math.abs(speed) > 1)
            error = Math.copySign(180, speed);
        float desired = clamp(.27F * error, 13);
        speed += clamp(desired - speed, 2.8F);
        if (Math.signum(speed) == Math.signum(error) && Math.abs(speed) > Math.abs(error))
            speed = error;
        heading += speed;
        // Keep sub-degree resolution even after many complete revolutions.
        if (Math.abs(heading) > 720) {
            float wrap = Math.copySign(360, heading);
            previousHeading -= wrap;
            heading -= wrap;
        }
        lead += .38F * (clamp(error * .46F, 26) - lead);
        float parent = 0;
        for (int j = 0; j <= LINKS; j++) {
            // Angles are relative to the moving body; subtract its rotation to retain world-space inertia.
            float inertial = previousBends[j] - speed;
            float lag = clamp((inertial - parent) * (j == 0 ? .53F : .42F), j == 0 ? 17 : 6);
            bends[j] = parent + lag;
            parent = bends[j];
        }
        float stride = pivotOnGround ? Math.min(1, Math.abs(speed) / 6) : 0;
        steps += .42F * (stride - steps);
        phase += Math.abs(speed) * RAD * 3.8F;
        if (phase > 2 * Math.PI) {
            phase -= (float) (2 * Math.PI);
            previousPhase -= (float) (2 * Math.PI);
        }
    }

    float heading(float partial, float fallback) {
        return initialized ? lerp(partial, previousHeading, heading) : fallback;
    }

    private static float clamp(float v, float limit) { return Math.max(-limit, Math.min(limit, v)); }
    private static float lerp(float t, float a, float b) { return a + (b - a) * t; }

    // Separate frame state from simulation so outlines and repeated render passes never advance the pose.
    static final class Frame {
        float lead;
        final float[] tail = new float[LINKS + 1];
        final float[] feet = new float[4];

        void sample(KavatTurn turn, float partial) {
            lead = -RAD * lerp(partial, turn.previousLead, turn.lead);
            for (int j = 0; j <= LINKS; j++)
                tail[j] = -RAD * lerp(partial, turn.previousBends[j], turn.bends[j]);
            float phase = lerp(partial, turn.previousPhase, turn.phase);
            float amplitude = lerp(partial, turn.previousSteps, turn.steps);
            for (int j = 0; j < 4; j++) {
                float swing = (float) Math.sin(phase + (j == 1 || j == 2 ? Math.PI : 0) + (j >= 2 ? .48 : 0));
                feet[j] = amplitude * Math.max(0, swing);
            }
        }
    }

    record Joint(float lead, int start, int end, int foot, float lift, float flex) {
        float yaw(Frame f) {
            return start < 0 ? lead * f.lead : f.tail[end] - (start == end ? 0 : f.tail[start]);
        }
        float pitch(Frame f) { return foot < 0 ? 0 : f.feet[foot] * flex; }
        float height(Frame f) { return foot < 0 ? 0 : f.feet[foot] * lift; }
    }

    static final Map<String, Joint> LONG = rig(12), SHORT = rig(8);

    private static Map<String, Joint> rig(int vaneCount) {
        var joints = new HashMap<String, Joint>();
        joints.put("ribs", new Joint(.29F, -1, -1, -1, 0, 0));
        joints.put("neck", new Joint(.48F, -1, -1, -1, 0, 0));
        joints.put("skull", new Joint(.23F, -1, -1, -1, 0, 0));
        joints.put("tail_base", new Joint(0, 0, 0, -1, 0, 0));
        chain(joints, "vane_", vaneCount);
        for (int i = 0; i < 7; i++) chain(joints, "plume_" + i + "_", 11);
        for (int i = 0; i < 5; i++) chain(joints, "tendril_" + i + "_", 8);
        String[] legs = {"foreL", "foreR", "hindL", "hindR"};
        for (int i = 0; i < legs.length; i++) {
            boolean fore = i < 2;
            joints.put(legs[i] + (fore ? "_upper" : "_hip"), new Joint(0, -1, -1, i, .61F, -.14F));
            joints.put(legs[i] + (fore ? "_elbow" : "_knee"), new Joint(0, -1, -1, i, 0, .27F));
            joints.put(legs[i] + (fore ? "_wrist" : "_hock"), new Joint(0, -1, -1, i, 0, -.12F));
        }
        return Map.copyOf(joints);
    }

    private static void chain(Map<String, Joint> joints, String prefix, int count) {
        int start = 0;
        for (int j = 1; j <= count; j++) {
            int end = Math.round((float) j * LINKS / count);
            joints.put(prefix + j, new Joint(0, start, end, -1, 0, 0));
            start = end;
        }
    }
}

