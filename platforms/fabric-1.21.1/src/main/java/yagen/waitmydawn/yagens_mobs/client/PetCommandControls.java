package yagen.waitmydawn.yagens_mobs.client;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Mob;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import yagen.waitmydawn.yagens_mobs.entity.command.PetOrders;
import yagen.waitmydawn.yagens_mobs.entity.command.PetRecall;

public final class PetCommandControls {
    private PetCommandControls() {}

    public interface Viewport {
        Frustum yagens$commandFrustum();
    }

    public static void disengage(Minecraft game) {
        if (game.player == null || game.level == null || game.screen != null || game.player.isSpectator()) return;
        Frustum view = ((Viewport)game.levelRenderer).yagens$commandFrustum();
        if (view == null) return;
        int[] batch = new int[PetOrders.BATCH];
        int count = 0;
        for (var entity : game.level.entitiesForRendering()) {
            if (!(entity instanceof Mob pet) || !PetOrders.belongsTo(pet, game.player)
                    || pet.isInvisibleTo(game.player) || !view.isVisible(pet.getBoundingBox())
                    || !game.player.hasLineOfSight(pet)) continue;
            batch[count++] = pet.getId();
            if (count == batch.length) {
                ClientPlayNetworking.send(new PetRecall(batch));
                batch = new int[PetOrders.BATCH];
                count = 0;
            }
        }
        if (count != 0) ClientPlayNetworking.send(new PetRecall(Arrays.copyOf(batch, count)));
    }
}
