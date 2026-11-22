package yagen.waitmydawn.yagens_mobs.core;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record SegmentApproach(double firstFraction, double secondFraction, double distanceSqr) {
    public static SegmentApproach between(Vec3 first, Vec3 direction, Vec3 second, Vec3 span) {
        Vec3 offset = first.subtract(second);
        double a = direction.lengthSqr();
        double b = direction.dot(span);
        double c = span.lengthSqr();
        double d = direction.dot(offset);
        double e = span.dot(offset);
        double determinant = a * c - b * b;
        double s = determinant > 1e-8 ? Mth.clamp((b * e - c * d) / determinant, 0, 1) : 0;
        double t = c > 1e-8 ? Mth.clamp((b * s + e) / c, 0, 1) : 0;
        // Reproject onto the first segment after the second segment clamps at an endpoint.
        s = a > 1e-8 ? Mth.clamp((b * t - d) / a, 0, 1) : 0;
        double distance = first.add(direction.scale(s)).distanceToSqr(second.add(span.scale(t)));
        return new SegmentApproach(s, t, distance);
    }
}

