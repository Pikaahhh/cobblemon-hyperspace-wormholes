package dev.hyperspacewormholes.hyperspace.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Access to Minecraft's pick-block action.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("pickBlock")
    void hyperspace_wormholes$pickBlock();
}
