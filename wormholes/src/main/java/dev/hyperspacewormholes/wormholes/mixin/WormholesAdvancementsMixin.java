package dev.hyperspacewormholes.wormholes.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.hyperspacewormholes.wormholes.WormholesAdvancements;
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
 * Works out what the Wormholes advancements tab shows with WormholesAdvancements.visibility instead of vanilla's
 * two-steps-ahead rule, and has the tab checked whenever a player's advancements load, so its first five show before
 * anything in it is done. The hyperspace half's PlayerAdvancementsMixin wraps the same call for the Hyperspace tab;
 * MixinExtras chains the two wraps, each handling only the tree with its own root and passing every other tree on, so
 * the order doesn't matter and every other tab still ends at vanilla's evaluator.
 */
@Mixin(PlayerAdvancements.class)
public class WormholesAdvancementsMixin {
    @Shadow
    private void markForVisibilityUpdate(AdvancementHolder advancement) {
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void hyperspace_wormholes$showWormholesRoot(ServerAdvancementManager manager, CallbackInfo ci) {
        AdvancementHolder root = manager.get(WormholesAdvancements.ROOT);
        if (root != null) markForVisibilityUpdate(root);
    }

    @WrapOperation(method = "updateTreeVisibility", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/advancements/AdvancementVisibilityEvaluator;evaluateVisibility(Lnet/minecraft/advancements/AdvancementNode;Ljava/util/function/Predicate;Lnet/minecraft/server/advancements/AdvancementVisibilityEvaluator$Output;)V"))
    private void hyperspace_wormholes$wormholesTabVisibility(AdvancementNode root, Predicate<AdvancementNode> isDone,
                                                             AdvancementVisibilityEvaluator.Output output, Operation<Void> original) {
        if (WormholesAdvancements.ours(root)) WormholesAdvancements.visibility(root, isDone, output);
        else original.call(root, isDone, output);
    }
}
