package dev.hyperspacewormholes.hyperspace.mixin;

import com.cobblemon.mod.common.item.crafting.CookingPotShapelessRecipe;
import dev.hyperspacewormholes.hyperspace.DonutBoosts;
import dev.hyperspacewormholes.hyperspace.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Cooking pot results. Remembers the berries put round a plain donut in the pot's grid, up to 8 like Legends Z-A's best
 * butter, since Cobblemon only records the seasoning slots, which donuts don't use. A donut that has its berries can't
 * go back in.
 */
@Mixin(value = CookingPotShapelessRecipe.class, remap = false)
public abstract class CookingPotShapelessRecipeMixin {
    @Inject(method = "matches", at = @At("HEAD"), cancellable = true)
    private void hyperspace_wormholes$plainDonutsOnly(CraftingInput input, Level level, CallbackInfoReturnable<Boolean> cir) {
        for (ItemStack stack : input.items()) {
            if (stack.is(ModItems.DONUT) && !DonutBoosts.ids(stack).isEmpty()) cir.setReturnValue(false);
        }
    }

    @Inject(method = "assemble", at = @At("RETURN"))
    private void hyperspace_wormholes$keepGridBerries(CraftingInput input, HolderLookup.Provider registries, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack result = cir.getReturnValue();
        if (!result.is(ModItems.DONUT)) return;
        List<String> berries = new ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (DonutBoosts.isBerry(id)) berries.add(id);
        }
        DonutBoosts.addGridBerries(result, berries);
    }
}
