package dev.hyperspacewormholes.hyperspace.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The key a mapping is bound to right now.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingKeyAccessor {
    @Accessor("key")
    InputConstants.Key hyperspace_wormholes$key();
}
