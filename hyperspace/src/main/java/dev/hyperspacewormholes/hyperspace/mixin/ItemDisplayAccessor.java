package dev.hyperspacewormholes.hyperspace.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Access to setting an item display's item.
 */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {
    @Invoker("setItemStack")
    void hyperspace_wormholes$setItem(ItemStack stack);
}
