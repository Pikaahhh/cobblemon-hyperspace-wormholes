package dev.hyperspacewormholes.hyperspace.mixin;

import dev.hyperspacewormholes.hyperspace.AdvancementLayout;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.TreeNodePosition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets AdvancementLayout put our pages' advancements on their spots once vanilla has laid a tab out. */
@Mixin(TreeNodePosition.class)
public class AdvancementLayoutMixin {
    @Inject(method = "run", at = @At("TAIL"))
    private static void hyperspace_wormholes$ourSpots(AdvancementNode root, CallbackInfo ci) {
        AdvancementLayout.place(root);
    }
}
