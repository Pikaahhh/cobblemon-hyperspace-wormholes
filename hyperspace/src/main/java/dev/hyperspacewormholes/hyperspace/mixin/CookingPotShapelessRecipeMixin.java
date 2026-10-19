package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.item.crafting.CookingPotShapelessRecipe;
import dev.hyperspacewormholes.hyperspace.DonutBoosts;
import dev.hyperspacewormholes.hyperspace.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps a Hoopa Ring's data when you put it in a cooking pot recipe, so re-boosting a ring doesn't wipe it.
 */
@Mixin(value = CookingPotShapelessRecipe.class, remap = false)
public abstract class CookingPotShapelessRecipeMixin {
    @Inject(method = "assemble", at = @At("RETURN"))
    private void hyperspace_wormholes$keepRing(CraftingInput input, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (!result.is(ModItems.HOOPA_RING)) return;
        for (ItemStack stack : input.items()) {
            if (stack.is(ModItems.HOOPA_RING)) {
                DonutBoosts.carryOver(stack, result);
                return;
            }
        }
    }
}
