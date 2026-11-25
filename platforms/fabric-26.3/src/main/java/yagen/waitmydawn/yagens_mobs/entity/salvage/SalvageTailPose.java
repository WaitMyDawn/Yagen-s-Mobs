package yagen.waitmydawn.yagens_mobs.entity.salvage;

import net.minecraft.util.Mth;

public final class SalvageTailPose {
    public static final int LINKS = 6;
    private static final float[] WEIGHTS = {.225F, .207F, .183F, .157F, .129F, .099F};
    private static final int DELAY = 4;
    private static final int SAMPLES = (LINKS - 1) * DELAY + 1;
    private final float[] angles = new float[3 * LINKS * 3];
    private final float[] previous = new float[angles.length];
    private final float[] velocity = new float[angles.length];
    private final float[][] history = new float[SAMPLES][3];
    private int sample;
    private float phase;
    private float pitchBefore;
    private float bankBefore;

    void tick(float pitch, float bank) {
        System.arraycopy(angles, 0, previous, 0, angles.length);
        // Substeps preserve the same settling time and phase lag between local hinges at any render FPS.
        for (int step = 1; step <= 3; step++) {
            spring(Mth.lerp(step / 3F, pitchBefore, pitch), Mth.lerp(step / 3F, bankBefore, bank));
        }
        pitchBefore = pitch;
        bankBefore = bank;
    }

    private void spring(float pitch, float bank) {
        history[sample][0] = Mth.clamp(pitch * 1.05F, -34, 34);
        history[sample][1] = Mth.clamp(bank * 1.24F, -36, 36);
        history[sample][2] = -.38F * bank;
        phase = (phase + .079F) % Mth.TWO_PI;
        for (int chain = 0; chain < 3; chain++) {
            for (int joint = 0; joint < LINKS; joint++) {
                float[] input = history[Math.floorMod(sample - joint * DELAY, SAMPLES)];
                float sway = phase - .63F * joint + (chain - 1) * .53F;
                float flex = .46F + .14F * joint;
                float frequency = 14.2F - 1.12F * joint;
                int offset = index(chain, joint, 0);
                integrate(offset, WEIGHTS[joint] * input[0] + flex * Mth.sin(sway + .53F), frequency);
                integrate(offset + 1, WEIGHTS[joint] * input[1] + flex * .69F * Mth.sin(sway), frequency);
                integrate(offset + 2, WEIGHTS[joint] * input[2] + flex * .67F * Mth.sin(sway - .66F), frequency);
            }
        }
        sample = (sample + 1) % SAMPLES;
    }

    private void integrate(int index, float target, float frequency) {
        float acceleration = frequency * frequency * (target - angles[index]) - 1.35F * frequency * velocity[index];
        velocity[index] += acceleration / 60;
        angles[index] += velocity[index] / 60;
    }

    private static int index(int chain, int joint, int axis) { return chain * LINKS * 3 + joint * 3 + axis; }

    public float rotation(int chain, int joint, int axis, float partialTick) {
        int i = index(chain, joint, axis);
        return Mth.lerp(partialTick, previous[i], angles[i]) * Mth.DEG_TO_RAD;
    }
}
