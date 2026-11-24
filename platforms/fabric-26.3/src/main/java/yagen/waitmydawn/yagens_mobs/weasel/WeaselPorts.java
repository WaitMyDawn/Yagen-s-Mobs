package yagen.waitmydawn.yagens_mobs.weasel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

final class WeaselPorts {
    static final boolean REFINED = false;
    static final boolean CREATE = loaded("create");
    private static final boolean IRON = loaded("ironchest");
    private static final boolean BACKPACKS = loaded("sophisticatedbackpacks") && loaded("sophisticatedcore");

    private WeaselPorts() {}

    private static boolean loaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }

    static boolean enabled() { return REFINED || CREATE; }

    static boolean handles(BlockEntity entity) {
        return (IRON || BACKPACKS) && FabricPhysicalStorage.supports(entity)
                || CREATE && FabricCreateStorage.supports(entity);
    }

    static @Nullable WeaselChests.Site at(ServerLevel level, BlockPos pos, ServerPlayer owner) {
        var entity = level.getBlockEntity(pos);
        if (entity == null || entity.isRemoved()) return null;
        if ((IRON || BACKPACKS) && FabricPhysicalStorage.supports(entity)) return FabricPhysicalStorage.at(level, entity, owner);
        if (CREATE && FabricCreateStorage.supports(entity)) return FabricCreateStorage.at(level, entity, owner);
        return null;
    }

    static @Nullable WeaselChests.Kind kind(BlockEntity entity) {
        if (CREATE && FabricCreateStorage.supports(entity)) return FabricCreateStorage.kind(entity);
        return null;
    }

    static boolean isRefined(BlockEntity entity) { return false; }
    static boolean isCreate(BlockEntity entity) { return CREATE && FabricCreateStorage.supports(entity); }
    static boolean isVaultPort(Level level, BlockEntity entity) {
        return isCreate(entity) && FabricCreateStorage.attachedToVault(level, entity);
    }

    static boolean physical(BlockEntity entity) {
        return (IRON || BACKPACKS) && FabricPhysicalStorage.supports(entity);
    }

    static @Nullable Object externalGroup(ServerLevel level, BlockPos pos) {
        return null;
    }

    static void lid(ServerLevel level, BlockPos pos, ServerPlayer owner, boolean open) {
        if ((IRON || BACKPACKS) && level.hasChunkAt(pos)) FabricPhysicalStorage.lid(level.getBlockEntity(pos), owner, open);
    }

}
