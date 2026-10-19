package dev.hyperspacewormholes.hyperspace.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Access to a post chain's passes, for the greyscale effect.
 */
@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Accessor("passes")
    List<PostPass> hyperspace_wormholes$passes();
}
