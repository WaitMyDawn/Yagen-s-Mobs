package yagen.waitmydawn.yagens_mobs.entity.vulture;

import net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;

public final class LongFeatherArrowItem extends ArrowItem {
    public LongFeatherArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isInfinite(ItemStack ammo, ItemStack bow, Player shooter) {
        return false;
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter) {
        return new LongFeatherArrow(level, shooter);
    }

    public void registerDispenser() {
        DispenserBlock.registerBehavior(this, new AbstractProjectileDispenseBehavior() {
            @Override
            protected Projectile getProjectile(Level level, Position pos, ItemStack ammo) {
                return new LongFeatherArrow(level, pos.x(), pos.y(), pos.z());
            }
        });
    }
}
