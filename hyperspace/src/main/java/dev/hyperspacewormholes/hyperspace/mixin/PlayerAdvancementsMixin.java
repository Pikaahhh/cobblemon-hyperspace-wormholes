package dev.hyperspacewormholes.hyperspace.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.hyperspacewormholes.hyperspace.HyperspaceAdvancements;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.advancements.AdvancementVisibilityEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Predicate;

/**
 * Works out what the Hyperspace advancements tab shows with HyperspaceAdvancements.visibility instead of vanilla's
 * two-steps-ahead rule, and has the tab checked whenever a player's advancements load, so its root shows before
 * anything in it is done. Every other tab is left to vanilla.
 */
@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {
    @Shadow
    private void markForVisibilityUpdate(AdvancementHolder advancement) {
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void hyperspace_wormholes$showRoot(ServerAdvancementManager manager, CallbackInfo ci) {
        AdvancementHolder root = manager.get(HyperspaceAdvancements.ROOT);
        if (root != null) markForVisibilityUpdate(root);
    }

    @WrapOperation(method = "updateTreeVisibility", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/advancements/AdvancementVisibilityEvaluator;evaluateVisibility(Lnet/minecraft/advancements/AdvancementNode;Ljava/util/function/Predicate;Lnet/minecraft/server/advancements/AdvancementVisibilityEvaluator$Output;)V"))
    private void hyperspace_wormholes$tabVisibility(AdvancementNode root, Predicate<AdvancementNode> isDone,
                                                    AdvancementVisibilityEvaluator.Output output, Operation<Void> original) {
        if (HyperspaceAdvancements.ours(root)) HyperspaceAdvancements.visibility(root, isDone, output);
        else original.call(root, isDone, output);
    }
}
