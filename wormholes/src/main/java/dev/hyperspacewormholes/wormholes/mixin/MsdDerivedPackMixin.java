package dev.hyperspacewormholes.wormholes.mixin;

import dev.hyperspacewormholes.wormholes.msd.MsdDerivedPack;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Puts {@link MsdDerivedPack} on top of every resource manager, so the Mega Showdown-derived assets are built at runtime. This is the one place client resources, world loading and /reload all pass through, and the list is what lets the pack read the files below it. */
@Mixin(MultiPackResourceManager.class)
public abstract class MsdDerivedPackMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static List<PackResources> hyperspace_wormholes$addDerivedPack(List<PackResources> packs) {
        return MsdDerivedPack.above(packs);
    }
}
