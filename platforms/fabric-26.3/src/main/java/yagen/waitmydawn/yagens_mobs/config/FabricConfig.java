package yagen.waitmydawn.yagens_mobs.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

final class FabricConfig {
    record Value<T>(String key, T defaultValue, Function<Object, T> decode, String comment) {
        T value(CommentedConfig config) {
            Object raw = config.get(key);
            return raw == null ? defaultValue : decode.apply(raw);
        }
    }

    private FabricConfig() {
    }

    static CommentedConfig load(Path path, List<Value<?>> definitions) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot create config directory for " + path, error);
        }
        try (var file = CommentedFileConfig.builder(path).sync().build()) {
            file.load();
            boolean dirty = false;
            for (var setting : definitions) {
                if (!file.contains(setting.key())) {
                    file.set(setting.key(), setting.defaultValue());
                    file.setComment(setting.key(), setting.comment());
                    dirty = true;
                }
            }
            if (dirty) file.save();
            return CommentedConfig.copy(file);
        }
    }

    static Value<Boolean> flag(String key, boolean fallback, String comment) {
        return new Value<>(key, fallback, raw -> raw instanceof Boolean flag ? flag : fallback, comment);
    }

    static Value<Integer> integer(String key, int fallback, int min, int max, String comment) {
        return new Value<>(key, fallback, raw -> raw instanceof Number number
                && number.doubleValue() == number.intValue() && number.intValue() >= min && number.intValue() <= max
                ? number.intValue() : fallback, comment);
    }

    static Value<Double> decimal(String key, double fallback, double min, double max, String comment) {
        return new Value<>(key, fallback, raw -> raw instanceof Number number
                && number.doubleValue() >= min && number.doubleValue() <= max ? number.doubleValue() : fallback, comment);
    }
}
