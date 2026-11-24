package yagen.waitmydawn.yagens_mobs.weasel.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface WeaselInventoryPosition {
    @Accessor("leftPos")
    int yagensMobs$left();

    @Accessor("topPos")
    int yagensMobs$top();
}
