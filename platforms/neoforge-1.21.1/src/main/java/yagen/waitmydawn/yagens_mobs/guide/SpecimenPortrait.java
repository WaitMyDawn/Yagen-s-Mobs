package yagen.waitmydawn.yagens_mobs.guide;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Orthographic texture rasterizer for dedicated servers without a GPU or the client entity dispatcher. */
final class SpecimenPortrait {
    private static final int W = 128, H = 96;
    private static final String[] FACES = {"north", "south", "west", "east", "up", "down"};
    private static final int[][] CORNERS = {
            {2, 3, 1, 0}, {7, 6, 4, 5}, {6, 2, 0, 4}, {3, 7, 5, 1}, {6, 7, 3, 2}, {0, 1, 5, 4}};
    private record Surface(Vector3f a, Vector3f b, Vector3f d, float u, float v, float du, float dv, float shade) {}
    private static InputStream asset(String path) throws IOException {
        var stream = SpecimenPortrait.class.getResourceAsStream("/assets/yagens_mobs/" + path);
        if (stream == null)
            throw new FileNotFoundException(path);
        return stream;
    }
    static byte[] render(String key) throws IOException {
        String[] v = key.split("/");
        boolean young = v[1].equals("young");
        String species = v[0], model = species, texture = species, age = young ? "_juvenile" : "";
        switch (species) {
            case "collector" -> {
                model = "collector/" + v[1];
                texture = model;
            }
            case "kavat" -> {
                model = "kavat/" + v[1] + "_" + v[3] + "_" + v[4];
                texture = "kavat/" + v[2];
            }
            case "condroc" -> {
                model += age;
                texture = model;
            }
            case "desert_skate" -> model += age;
            case "snowcache_weasel" -> {
                model += age;
                texture = model + "_" + v[2];
            }
            case "velocipod" -> {
                model += age;
                texture = species + "/" + v[2];
            }
            case "lumen_butterfly" -> {
                model += young ? "_juvenile" : "_adult";
                texture = model + "_" + v[2];
            }
            case "salvage_ray" -> {
                model += "_" + v[3] + "_tail";
                texture = species + "_" + v[2];
            }
            case "bubble_salamander" -> {
                if (!v[2].equals("green"))
                    texture += "/" + (v[2].equals("mixed") ? "mixed_0" : v[2]);
            }
            case "rock_hanger" -> {
            }
            default -> throw new IOException("Missing portrait model for " + species);
        }
        try (var geo = asset("geo/entity/" + model + ".geo.json");
                var png = asset("textures/entity/" + texture + ".png")) {
            var object = JsonParser.parseReader(new InputStreamReader(geo, StandardCharsets.UTF_8))
                                 .getAsJsonObject()
                                 .getAsJsonArray("minecraft:geometry")
                                 .get(0)
                                 .getAsJsonObject();
            var tex = ImageIO.read(png);
            var desc = object.getAsJsonObject("description");
            float us = (float) tex.getWidth() / desc.get("texture_width").getAsInt(),
                  vs = (float) tex.getHeight() / desc.get("texture_height").getAsInt();
            List<Surface> surfaces = new ArrayList<>();
            Map<String, Matrix4f> transforms = new HashMap<>();
            for (var value : object.getAsJsonArray("bones")) {
                var bone = value.getAsJsonObject();
                String name = bone.get("name").getAsString();
                var mat = bone.has("parent") ? new Matrix4f(transforms.get(bone.get("parent").getAsString()))
                                             : new Matrix4f();
                var pivot = vector(bone, "pivot");
                mat.translate(pivot);
                rotate(mat, bone);
                if (species.equals("bubble_salamander") && young) {
                    switch (name) {
                        case "head" -> mat.scale(1.16f);
                        case "air_bladder" -> mat.scale(.82f, .78f, .82f);
                        case "tail_base" -> mat.scale(1, 1, .73f);
                    }
                }
                mat.translate(-pivot.x, -pivot.y, -pivot.z);
                transforms.put(name, mat);
                if (!bone.has("cubes") || (name.equals("female_frills") && !v[v.length - 1].equals("female")))
                    continue;
                for (var c : bone.getAsJsonArray("cubes")) {
                    var cube = c.getAsJsonObject();
                    var origin = vector(cube, "origin");
                    var size = vector(cube, "size");
                    float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0;
                    var p = vector(cube, "pivot");
                    var m = new Matrix4f(mat).translate(p);
                    rotate(m, cube);
                    m.translate(-p.x, -p.y, -p.z);
                    var points = new Vector3f[8];
                    for (int i = 0; i < 8; i++) {
                        var world = m.transformPosition(
                                new Vector3f(origin.x + ((i & 1) == 0 ? -inflate : size.x + inflate),
                                        origin.y + ((i & 2) == 0 ? -inflate : size.y + inflate),
                                        origin.z + ((i & 4) == 0 ? -inflate : size.z + inflate)));
                        points[i] = new Vector3f(.8f * world.x + .6f * world.z,
                                .216f * world.x - .933f * world.y - .288f * world.z,
                                .56f * world.x + .36f * world.y - .747f * world.z);
                    }
                    var faces = cube.getAsJsonObject("uv");
                    for (int i = 0; i < 6; i++) {
                        var f = faces.getAsJsonObject(FACES[i]);
                        if (f == null)
                            continue;
                        var uv = f.getAsJsonArray("uv");
                        var span = f.getAsJsonArray("uv_size");
                        if (span == null)
                            continue;
                        var idx = CORNERS[i];
                        surfaces.add(new Surface(points[idx[0]], points[idx[1]], points[idx[3]],
                                uv.get(0).getAsFloat() * us, uv.get(1).getAsFloat() * vs, span.get(0).getAsFloat() * us,
                                span.get(1).getAsFloat() * vs, switch (i) {
                                    case 4 -> 1f;
                                    case 5 -> .68f;
                                    case 2, 3 -> .83f;
                                    default -> .92f;
                                }));
                    }
                }
            }
            float x0 = Float.MAX_VALUE, x1 = -x0, y0 = x0, y1 = -x0;
            for (var s : surfaces)
                for (var p : List.of(s.a, s.b, s.d, new Vector3f(s.b).add(s.d).sub(s.a))) {
                    x0 = Math.min(x0, p.x);
                    x1 = Math.max(x1, p.x);
                    y0 = Math.min(y0, p.y);
                    y1 = Math.max(y1, p.y);
                }
            float scale = Math.min((W - 8) / Math.max(.01f, x1 - x0), (H - 8) / Math.max(.01f, y1 - y0));
            if (young && species.equals("salvage_ray"))
                scale *= .72f;
            var img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            float[] depth = new float[W * H];
            Arrays.fill(depth, Float.NEGATIVE_INFINITY);
            for (var s : surfaces)
                raster(s, scale, (W - scale * (x0 + x1)) / 2, (H - scale * (y0 + y1)) / 2, img, tex, depth);
            var out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        }
    }

    private static Vector3f vector(JsonObject obj, String key) {
        var a = obj.getAsJsonArray(key);
        return a == null ? new Vector3f()
                         : new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }
    private static void rotate(Matrix4f m, JsonObject obj) {
        // Bedrock stores clockwise pitch and roll in these model coordinates.
        var r = vector(obj, "rotation").mul((float) Math.PI / 180);
        m.rotateZYX(-r.z, r.y, -r.x);
    }
    private static void raster(
            Surface s, float scale, float ox, float oy, BufferedImage img, BufferedImage tex, float[] depth) {
        float ax = s.a.x * scale + ox, ay = s.a.y * scale + oy, ux = (s.b.x - s.a.x) * scale,
              uy = (s.b.y - s.a.y) * scale, vx = (s.d.x - s.a.x) * scale, vy = (s.d.y - s.a.y) * scale,
              det = ux * vy - uy * vx;
        if (Math.abs(det) < 1e-6f)
            return;
        int loX = Math.max(0, (int) Math.floor(ax + Math.min(0, ux) + Math.min(0, vx))),
            hiX = Math.min(W - 1, (int) Math.ceil(ax + Math.max(0, ux) + Math.max(0, vx)));
        int loY = Math.max(0, (int) Math.floor(ay + Math.min(0, uy) + Math.min(0, vy))),
            hiY = Math.min(H - 1, (int) Math.ceil(ay + Math.max(0, uy) + Math.max(0, vy)));
        for (int y = loY; y <= hiY; y++)
            for (int x = loX; x <= hiX; x++) {
                float dx = x + .5f - ax, dy = y + .5f - ay, u = (dx * vy - dy * vx) / det,
                      v = (ux * dy - uy * dx) / det;
                if (u < 0 || u >= 1 || v < 0 || v >= 1)
                    continue;
                float z = s.a.z + u * (s.b.z - s.a.z) + v * (s.d.z - s.a.z);
                int idx = y * W + x;
                if (z < depth[idx])
                    continue;
                int argb = tex.getRGB(Math.clamp((int) Math.floor(s.u + u * s.du), 0, tex.getWidth() - 1),
                        Math.clamp((int) Math.floor(s.v + v * s.dv), 0, tex.getHeight() - 1));
                if ((argb >>> 24) < 32)
                    continue;
                depth[idx] = z;
                img.setRGB(x, y,
                        (argb & 0xff000000) | ((int) ((argb >> 16 & 255) * s.shade) << 16)
                                | ((int) ((argb >> 8 & 255) * s.shade) << 8) | (int) ((argb & 255) * s.shade));
            }
    }
    private SpecimenPortrait() {}
}
