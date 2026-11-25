package yagen.waitmydawn.yagens_mobs.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerGamePacketListenerImpl.class)
public interface CompanionTeleportSequence {
    @Accessor("awaitingTeleport")
    int yagens$teleportSequence();
}
