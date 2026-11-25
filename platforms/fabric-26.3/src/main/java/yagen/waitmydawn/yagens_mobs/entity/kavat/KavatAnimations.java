package yagen.waitmydawn.yagens_mobs.entity.kavat;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import com.geckolib.cache.animation.Animation;
import com.geckolib.cache.animation.BoneAnimation;
import com.geckolib.cache.animation.KeyframeStack;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.animation.BakedAnimations;
import yagen.waitmydawn.yagens_mobs.core.ModIdentity;

public final class KavatAnimations extends SimplePreparableReloadListener<KavatAnimations.Index> {
    public static final KavatAnimations INSTANCE = new KavatAnimations();
    static final Identifier LIBRARY = id("entity/kavat_library");
    private static final Identifier INDEX = id("kavat/animation_index.json");
    private static final KeyframeStack EMPTY = KeyframeStack.EMPTY;
    private static final Gson GSON = new Gson();

    private record Binding(String bone, int rotation, int position, int scale) {}
    private record Clip(int header, int[] parts) {}
    record Index(Binding[] bindings, int[][] parts, Clip[] clips, Map<String, Map<String, Integer>> rigs) {}

    private Index index;
    private BakedAnimations library;
    private BoneAnimation[] tracks;
    private BoneAnimation[] bindings;
    private Animation[] clips;

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ModIdentity.ID, path);
    }

    @Override
    protected Index prepare(ResourceManager manager, ProfilerFiller profiler) {
        try (var reader = manager.openAsReader(INDEX)) {
            return GSON.fromJson(reader, Index.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot load " + INDEX, ex);
        }
    }

    @Override
    protected void apply(Index prepared, ResourceManager manager, ProfilerFiller profiler) {
        index = prepared;
        library = null;
        tracks = null;
        bindings = null;
        clips = null;
    }

    Animation resolve(String rig, String name) {
        // Existing resource packs may replace individual actions at the original per-rig path.
        var baked = GeckoLibResources.getBakedAnimations().cache();
        var override = baked.get(id("entity/kavat/" + rig));
        if (override != null) {
            var replacement = override.getAnimation(name);
            if (replacement != null) return replacement;
        }

        var actions = index.rigs().get(rig);
        Integer slot = actions == null ? null : actions.get(name);
        if (slot == null) return null;
        var current = baked.get(LIBRARY);
        if (current == null) throw new IllegalStateException("Missing GeckoLib resource " + LIBRARY);
        if (library != current) bindLibrary(current);

        if (clips[slot] == null) clips[slot] = compose(name, index.clips()[slot]);
        return clips[slot];
    }

    private void bindLibrary(BakedAnimations current) {
        // GeckoLib and this listener reload independently. Resolve against the committed bank on first use.
        var bank = current.getAnimation("track_bank").boneAnimations();
        tracks = new BoneAnimation[bank.length];
        for (var entry : bank) tracks[Integer.parseInt(entry.boneName())] = entry;
        bindings = new BoneAnimation[index.bindings().length];
        clips = new Animation[index.clips().length];
        library = current;
    }

    private BoneAnimation binding(int slot) {
        var bone = bindings[slot];
        if (bone == null) {
            var b = index.bindings()[slot];
            bone = new BoneAnimation(b.bone(),
                    b.rotation() < 0 ? EMPTY : tracks[b.rotation()].rotationKeyFrames(),
                    b.position() < 0 ? EMPTY : tracks[b.position()].positionKeyFrames(),
                    b.scale() < 0 ? EMPTY : tracks[b.scale()].scaleKeyFrames());
            bindings[slot] = bone;
        }
        return bone;
    }

    private Animation compose(String name, Clip clip) {
        var header = library.getAnimation("header_" + clip.header());
        int size = 0;
        for (int part : clip.parts()) size += index.parts()[part].length;
        var bones = new BoneAnimation[size];
        int next = 0;
        for (int part : clip.parts()) {
            for (int slot : index.parts()[part]) bones[next++] = binding(slot);
        }
        return Animation.create(name, header.length(), header.loopType(), bones, header.keyframeMarkers());
    }
}
