package dev.hyperspacewormholes.wormholes.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Access to changing a text display's text, for the Item Analyser's cooldown timer.
 */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Invoker("setText")
    void hyperspace_wormholes$setText(Component text);
}
