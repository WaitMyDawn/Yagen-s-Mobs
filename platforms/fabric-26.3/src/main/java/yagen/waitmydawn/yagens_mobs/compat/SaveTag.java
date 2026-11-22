package yagen.waitmydawn.yagens_mobs.compat;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class SaveTag {
    // Spread the existing mod fields into the native value store to retain their entity NBT paths.
    private static final MapCodec<CompoundTag> FIELDS = new MapCodec<>() {
        @Override
        public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> data) {
            return CompoundTag.CODEC.parse(ops, ops.createMap(data.entries()));
        }

        @Override
        public <T> RecordBuilder<T> encode(CompoundTag tag, DynamicOps<T> ops, RecordBuilder<T> output) {
            var encoded = CompoundTag.CODEC.encodeStart(ops, tag).flatMap(ops::getMap);
            if (encoded.result().isPresent()) {
                encoded.result().orElseThrow().entries()
                        .forEach(entry -> output.add(entry.getFirst(), entry.getSecond()));
            }
            return output.withErrorsFrom(encoded);
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }
    };

    private SaveTag() {}

    public static CompoundTag read(ValueInput input) {
        return input.read(FIELDS).orElseGet(CompoundTag::new);
    }

    public static void write(ValueOutput output, CompoundTag tag) {
        output.store(FIELDS, tag);
    }

    public static Optional<UUID> id(CompoundTag tag, String key) {
        return tag.read(key, UUIDUtil.CODEC);
    }

    public static void putId(CompoundTag tag, String key, UUID id) {
        tag.store(key, UUIDUtil.CODEC, id);
    }

    public static ValueInput input(HolderLookup.Provider lookup, CompoundTag tag) {
        return TagValueInput.create(ProblemReporter.DISCARDING, lookup, tag);
    }

    public static net.minecraft.nbt.Tag saveItem(DynamicOps<net.minecraft.nbt.Tag> ops, net.minecraft.world.item.ItemStack item) {
        return net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, item).getOrThrow();
    }

    public static TagValueOutput output(HolderLookup.Provider lookup) {
        return TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
    }
}
