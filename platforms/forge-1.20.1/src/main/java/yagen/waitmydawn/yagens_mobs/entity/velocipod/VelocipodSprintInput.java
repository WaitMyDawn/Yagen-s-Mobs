package yagen.waitmydawn.yagens_mobs.entity.velocipod;

import net.minecraft.server.level.ServerPlayer;

public record VelocipodSprintInput(int vehicle, boolean held) {
    public void handle(ServerPlayer rider) {
        if (rider != null && rider.getVehicle() instanceof Velocipod mount && mount.getId() == vehicle) {
            mount.setRiderSprint(rider, held);
        }
    }
}
