package yagen.waitmydawn.yagens_mobs.salvage;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import yagen.waitmydawn.yagens_mobs.config.ServerConfigs;

public final class SalvageNestBoxEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private UUID id = UUID.randomUUID();
    private UUID owner;
    private Component label;
    private final Set<UUID> rescue = new HashSet<>();
    private BlockPos patrol;
    private UUID patrolId;
    private boolean guard;
    private boolean wildSpawn;

    public SalvageNestBoxEntity(BlockPos pos, BlockState state) {
        super(SalvageRays.NEST_BOX_ENTITY.get(), pos, state);
    }

    UUID id() {
        return id;
    }

    SalvageNestDirectory.End endpoint() {
        return new SalvageNestDirectory.End(worldPosition, id);
    }

    public Vec3 perch() {
        return Vec3.atBottomCenterOf(worldPosition.above()).add(0, .24, 0);
    }

    public Vec3 servicePoint() {
        return perch().add(0, 1.9, 0);
    }

    boolean guard() {
        return guard;
    }

    void toggleGuard() {
        guard = !guard;
        setChanged();
    }

    boolean allowsRescue(UUID player) {
        return rescue.contains(player);
    }

    boolean toggleRescue(UUID player) {
        boolean added = rescue.add(player);
        if (!added) {
            rescue.remove(player);
        }
        setChanged();
        return added;
    }

    void setOwner(UUID player) {
        owner = player;
        setChanged();
    }

    boolean mayConfigure(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2);
    }

    void nameChanged(@Nullable Component name) {
        label = name;
        setChanged();
        remember();
    }

    private void importLegacyLink(ServerLevel level) {
        if (patrol == null) {
            return;
        }
        SalvageNestBoxEntity partner = SalvageNestDirectory.loaded(level, patrol);
        if (partner != null && patrolId.equals(partner.id) && id.equals(partner.patrolId)) {
            partner.remember();
            SalvageNestDirectory.of(level).connect(endpoint(), partner.endpoint());
            patrol = partner.patrol = null;
            patrolId = partner.patrolId = null;
            partner.setChanged();
            setChanged();
        }
    }

    int deposit(ItemStack carrying) {
        int before = carrying.getCount();
        for (int pass = 0; pass < 2 && !carrying.isEmpty(); pass++) {
            for (int i = 0; i < items.size() && !carrying.isEmpty(); i++) {
                ItemStack existing = items.get(i);
                if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, carrying)) {
                    int count = Math.min(carrying.getCount(), Math.min(existing.getMaxStackSize(), getMaxStackSize()) - existing.getCount());
                    if (count > 0) {
                        existing.grow(count);
                        carrying.shrink(count);
                    }
                } else if (pass == 1 && existing.isEmpty()) {
                    items.set(i, carrying.split(Math.min(carrying.getMaxStackSize(), getMaxStackSize())));
                }
            }
        }
        int inserted = before - carrying.getCount();
        if (inserted > 0) {
            setChanged();
        }
        return inserted;
    }

    boolean hasChorusFruit() {
        return countItem(Items.CHORUS_FRUIT) > 0;
    }

    void consumeChorusFruit() {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (getItem(slot).is(Items.CHORUS_FRUIT)) {
                removeItem(slot, 1);
                return;
            }
        }
    }

    void seedWildGroup() {
        wildSpawn = true;
        setChanged();
    }

    void ready(ServerLevel level) {
        remember();
        importLegacyLink(level);
        if (!wildSpawn) {
            return;
        }
        wildSpawn = false;
        setChanged();
        var settings = ServerConfigs.SALVAGE_RAY;
        if (!settings.canSpawn()) {
            return;
        }
        int min = settings.minGroupSize().get();
        int count = min + level.random.nextInt(settings.maxGroupCount() - min + 1);
        for (int i = 0; i < count; i++) {
            SalvageRay ray = SalvageRays.RAY.get().create(level);
            if (ray == null) {
                continue;
            }
            Vec3 launch = servicePoint().add((i % 3 - 1) * 1.45, .35, (i / 3 - 1) * 1.45);
            ray.moveTo(launch.x, launch.y, launch.z, level.random.nextFloat() * 360, 0);
            ray.finalizeSpawn(level, level.getCurrentDifficultyAt(worldPosition), MobSpawnType.STRUCTURE, null);
            ray.setHome(this);
            level.addFreshEntity(ray);
        }
    }

    void forget(ServerLevel level) {
        SalvageNestDirectory.of(level).remove(worldPosition, id);
    }

    private void remember() {
        if (level instanceof ServerLevel server) {
            Component custom = getCustomName();
            SalvageNestDirectory.of(server).remember(new SalvageNestDirectory.Site(
                    worldPosition, id, custom == null ? "" : custom.getString(), owner));
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> slots) {
        items = slots;
    }

    @Override
    public Component getName() {
        return label == null ? super.getName() : label;
    }

    @Override
    public @Nullable Component getCustomName() {
        return label == null ? super.getCustomName() : label;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.yagens_mobs.salvage_nest_box");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new SalvageNestMenu(containerId, inventory, this, worldPosition, id, mayConfigure(inventory.player));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putUUID("Id", id);
        if (label != null) {
            tag.putString("Label", Component.Serializer.toJson(label, registries));
        }
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (patrol != null && patrolId != null) {
            tag.putLong("Patrol", patrol.asLong());
            tag.putUUID("PatrolId", patrolId);
        }
        ListTag signups = new ListTag();
        rescue.forEach(player -> signups.add(NbtUtils.createUUID(player)));
        tag.put("Rescue", signups);
        tag.putBoolean("Guard", guard);
        tag.putBoolean("WildSpawn", wildSpawn);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        if (tag.hasUUID("Id")) {
            id = tag.getUUID("Id");
        }
        label = tag.contains("Label") ? parseCustomNameSafe(tag.getString("Label"), registries) : null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        patrolId = tag.hasUUID("PatrolId") ? tag.getUUID("PatrolId") : null;
        patrol = patrolId == null ? null : BlockPos.of(tag.getLong("Patrol"));
        rescue.clear();
        for (Tag player : tag.getList("Rescue", Tag.TAG_INT_ARRAY)) {
            rescue.add(NbtUtils.loadUUID(player));
        }
        guard = tag.getBoolean("Guard");
        wildSpawn = tag.getBoolean("WildSpawn");
    }
}
